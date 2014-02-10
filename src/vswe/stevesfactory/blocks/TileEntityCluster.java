package vswe.stevesfactory.blocks;


import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.ForgeDirection;
import vswe.stevesfactory.network.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileEntityCluster extends TileEntity implements ITileEntityInterface, IPacketBlock {

    private boolean requestedInfo;
    private List<TileEntityClusterElement> elements;
    private List<ClusterRegistry> registryList;
    private Map<ClusterMethodRegistration, List<ClusterRegistry>> methodRegistration;
    private ITileEntityInterface interfaceObject;  //only the relay is currently having a interface

    public TileEntityCluster() {
        elements = new ArrayList<TileEntityClusterElement>();
        registryList = new ArrayList<ClusterRegistry>();
        methodRegistration = new HashMap<ClusterMethodRegistration, List<ClusterRegistry>>();
        for (ClusterMethodRegistration clusterMethodRegistration : ClusterMethodRegistration.values()) {
            methodRegistration.put(clusterMethodRegistration, new ArrayList<ClusterRegistry>());
        }
    }

    public void loadElements(ItemStack itemStack) {
        NBTTagCompound compound = itemStack.getTagCompound();

        if (compound != null && compound.hasKey(ItemCluster.NBT_CABLE)) {
            NBTTagCompound cable = compound.getCompoundTag(ItemCluster.NBT_CABLE);
            byte[] types = cable.getByteArray(ItemCluster.NBT_TYPES);
            loadElements(types);
        }
    }

    private void loadElements(byte[] types) {
        registryList.clear();
        elements.clear();

        for (byte type : types) {
            ClusterRegistry block = ClusterRegistry.getRegistryList().get(type);
            registryList.add(block);
            TileEntityClusterElement element = (TileEntityClusterElement)block.getBlock().createNewTileEntity(getWorldObj());
            elements.add(element);
            if (element instanceof ITileEntityInterface) {
                interfaceObject = (ITileEntityInterface)element;
            }
            for (ClusterMethodRegistration clusterMethodRegistration : element.getRegistrations()) {
                methodRegistration.get(clusterMethodRegistration).add(block);
            }
            element.xCoord = xCoord;
            element.yCoord = yCoord;
            element.zCoord = zCoord;
            element.worldObj = worldObj;
            element.setPartOfCluster(true);
        }
    }

    public List<TileEntityClusterElement> getElements() {
        return elements;
    }


    @Override
    public void updateEntity() {
        for (TileEntityClusterElement element : elements) {
            if (element.worldObj == null) {
                element.worldObj = this.worldObj;
            }
            element.updateEntity();
        }

        if (!requestedInfo && worldObj.isRemote) {
            requestedInfo = true;
            requestData();
        }
    }

    @SideOnly(Side.CLIENT)
    private void requestData() {
        PacketHandler.sendBlockPacket(this, Minecraft.getMinecraft().thePlayer, 0);
    }

    private List<ClusterRegistry> getRegistrations(ClusterMethodRegistration method) {
        return methodRegistration.get(method);
    }

    public void onBlockPlacedBy(EntityLivingBase entity, ItemStack itemStack) {
        for (ClusterRegistry blockContainer :  getRegistrations(ClusterMethodRegistration.ON_BLOCK_PLACED_BY)) {
            blockContainer.getBlock().onBlockPlacedBy(worldObj, xCoord, yCoord, zCoord, entity, blockContainer.getItemStack(false));
        }
    }

    public void onNeighborBlockChange(int id) {
        for (ClusterRegistry blockContainer : getRegistrations(ClusterMethodRegistration.ON_NEIGHBOR_BLOCK_CHANGED)) {
            blockContainer.getBlock().onNeighborBlockChange(worldObj, xCoord, yCoord, zCoord, id);
        }
    }

    public boolean canConnectRedstone(int side) {
        for (ClusterRegistry blockContainer : getRegistrations(ClusterMethodRegistration.CAN_CONNECT_REDSTONE)) {
            if (blockContainer.getBlock().canConnectRedstone(worldObj, xCoord, yCoord, zCoord, side)) {
                return true;
            }
        }

        return false;
    }

    public void onBlockAdded() {
        for (ClusterRegistry blockContainer : getRegistrations(ClusterMethodRegistration.ON_BLOCK_ADDED)) {
            blockContainer.getBlock().onBlockAdded(worldObj, xCoord, yCoord, zCoord);
        }
    }

    public boolean shouldCheckWeakPower(int side) {
        for (ClusterRegistry blockContainer : getRegistrations(ClusterMethodRegistration.SHOULD_CHECK_WEAK_POWER)) {
            if (blockContainer.getBlock().shouldCheckWeakPower(worldObj, xCoord, yCoord, zCoord, side)) {
                return true;
            }
        }

        return false;
    }


    public int isProvidingWeakPower(int side) {
        int max = 0;

        for (ClusterRegistry blockContainer : getRegistrations(ClusterMethodRegistration.IS_PROVIDING_WEAK_POWER)) {
            max = Math.max(max, blockContainer.getBlock().isProvidingWeakPower(worldObj, xCoord, yCoord, zCoord, side));
        }

        return max;
    }

    public int isProvidingStrongPower(int side) {
        int max = 0;

        for (ClusterRegistry blockContainer : getRegistrations(ClusterMethodRegistration.IS_PROVIDING_STRONG_POWER)) {
            max = Math.max(max, blockContainer.getBlock().isProvidingStrongPower(worldObj, xCoord, yCoord, zCoord, side));
        }

        return max;
    }

    public boolean onBlockActivated(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        for (ClusterRegistry blockContainer : getRegistrations(ClusterMethodRegistration.ON_BLOCK_ACTIVATED)) {
            if (blockContainer.getBlock().onBlockActivated(worldObj, xCoord, yCoord, zCoord, player, side, hitX, hitY, hitZ)) {
                return true;
            }
        }

        return false;
    }


    public static <T> T getTileEntity(Class<? extends TileEntityClusterElement> clazz, IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getBlockTileEntity(x, y, z);

        if (te != null) {
            if (clazz.isInstance(te)) {
                return (T)te;
            }else if(te instanceof TileEntityCluster) {
                for (TileEntityClusterElement element : ((TileEntityCluster) te).getElements()) {
                    if (clazz.isInstance(element)) {
                        return (T)element;
                    }
                }
            }
        }

        return null;
    }


    @Override
    public Container getContainer(TileEntity te, InventoryPlayer inv) {
        return interfaceObject == null ? null : interfaceObject.getContainer((TileEntity)interfaceObject, inv);
    }

    @Override
    public GuiScreen getGui(TileEntity te, InventoryPlayer inv) {
        return interfaceObject == null ? null : interfaceObject.getGui((TileEntity) interfaceObject, inv);
    }

    @Override
    public void readAllData(DataReader dr, EntityPlayer player) {
        if (interfaceObject != null) {
            interfaceObject.readAllData(dr, player);
        }
    }

    @Override
    public void readUpdatedData(DataReader dr, EntityPlayer player) {
        if (interfaceObject != null) {
            interfaceObject.readUpdatedData(dr, player);
        }
    }

    @Override
    public void writeAllData(DataWriter dw) {
        if (interfaceObject != null) {
            interfaceObject.writeAllData(dw);
        }
    }

    private static final String NBT_SUB_BLOCKS = "SubBlocks";
    private static final String NBT_SUB_BLOCK_ID = "SubId";
    private static final String NBT_SUB_BLOCK_META = "SubMeta";

    @Override
    public void writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);

        NBTTagList subList = new NBTTagList();
        for (int i = 0; i < elements.size(); i++) {
            TileEntityClusterElement element = elements.get(i);
            ClusterRegistry registryElement = registryList.get(i);
            NBTTagCompound sub = new NBTTagCompound();
            sub.setByte(NBT_SUB_BLOCK_ID, (byte)registryElement.getId());
            sub.setByte(NBT_SUB_BLOCK_META, (byte)element.getBlockMetadata());
            element.writeContentToNBT(sub);

            subList.appendTag(sub);
        }


        tagCompound.setTag(NBT_SUB_BLOCKS, subList);
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);

        NBTTagList subList = tagCompound.getTagList(NBT_SUB_BLOCKS);
        List<Byte> bytes = new ArrayList<Byte>();
        for (int i = 0; i < subList.tagCount(); i++) {
            NBTTagCompound sub = (NBTTagCompound)subList.tagAt(i);
            bytes.add(sub.getByte(NBT_SUB_BLOCK_ID));
        }
        byte[] byteArr = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            byteArr[i] = bytes.get(i);
        }
        loadElements(byteArr);
        for (int i = 0; i < subList.tagCount(); i++) {
            NBTTagCompound sub = (NBTTagCompound)subList.tagAt(i);
            TileEntityClusterElement element = elements.get(i);
            element.setMetaData(sub.getByte(NBT_SUB_BLOCK_META));
            element.readContentFromNBT(sub);
        }
    }

    @Override
    public void writeData(DataWriter dw, EntityPlayer player, boolean onServer, int id) {
        if (onServer) {
            dw.writeData(elements.size(), DataBitHelper.CLUSTER_SUB_ID);
            for (int i = 0; i < elements.size(); i++) {
                dw.writeData((byte)registryList.get(i).getId(), DataBitHelper.CLUSTER_SUB_ID);
            }
            for (int i = 0; i < elements.size(); i++) {
                dw.writeData((byte)elements.get(i).getBlockMetadata(), DataBitHelper.BLOCK_META);
            }
        }else{
            //nothing to write, empty packet
        }
    }

    @Override
    public void readData(DataReader dr, EntityPlayer player, boolean onServer, int id) {
        if (onServer) {
            //respond by sending the data to the client that required it
            PacketHandler.sendBlockPacket(this, player, 0);
        }else{
            int length = dr.readData(DataBitHelper.CLUSTER_SUB_ID);
            byte[] types = new byte[length];
            for (int i = 0; i < length; i++) {
                types[i] = (byte)dr.readData(DataBitHelper.CLUSTER_SUB_ID);
            }
            loadElements(types);
            for (int i = 0; i < length; i++) {
                elements.get(i).setMetaData(dr.readData(DataBitHelper.BLOCK_META));
            }
        }
    }

    @Override
    public int infoBitLength(boolean onServer) {
        return 0;
    }
}