package com.songoda.epichoppers.listeners;

import com.songoda.epichoppers.EpicHoppers;
import com.songoda.epichoppers.hopper.Filter;
import com.songoda.epichoppers.hopper.Hopper;
import com.songoda.epichoppers.hopper.levels.Level;
import com.songoda.epichoppers.utils.Methods;
import com.songoda.epichoppers.utils.ServerVersion;
import com.songoda.epichoppers.utils.TeleportTrigger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;

/**
 * Created by songoda on 3/14/2017.
 */
public class BlockListeners implements Listener {

    private final EpicHoppers instance;

    public BlockListeners(EpicHoppers instance) {
        this.instance = instance;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
            Player player = e.getPlayer();

            if (e.getBlock().getType() != Material.HOPPER) return;

            if (instance.isLiquidtanks() && net.arcaniax.liquidtanks.object.LiquidTankAPI.isLiquidTank(e.getBlock().getLocation()))
                return;

            int amt = count(e.getBlock().getChunk());

            int max = maxHoppers(player);

            if (max != -1 && amt > max) {
                player.sendMessage(instance.getLocale().getMessage("event.hopper.toomany", max));
                e.setCancelled(true);
                return;
            }

            if (!e.getItemInHand().getItemMeta().hasDisplayName()) return;

            ItemStack item = e.getItemInHand().clone();

            instance.getHopperManager().addHopper(e.getBlock().getLocation(), new Hopper(e.getBlock(), instance.getLevelManager().getLevel(item), player.getUniqueId(), player.getUniqueId(), new ArrayList<>(), new Filter(), TeleportTrigger.DISABLED, null));
    }

    private int maxHoppers(Player player) {
        int limit = -1;
        for (PermissionAttachmentInfo permissionAttachmentInfo : player.getEffectivePermissions()) {
            if (!permissionAttachmentInfo.getPermission().toLowerCase().startsWith("epichoppers.limit")) continue;
            limit = Integer.parseInt(permissionAttachmentInfo.getPermission().split("\\.")[2]);
        }
        if (limit == -1) limit = instance.getConfig().getInt("Main.Max Hoppers Per Chunk");
        return limit;
    }

    private int count(Chunk c) {
            int count = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < c.getWorld().getMaxHeight(); y++) {
                        if (c.getBlock(x, y, z).getType() == Material.HOPPER) count++;
                    }
                }
            }
            return count;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
            Block block = event.getBlock();
            Player player = event.getPlayer();

            handleSyncTouch(event);

            if (event.getBlock().getType() != Material.HOPPER) return;

            if (instance.isLiquidtanks() && net.arcaniax.liquidtanks.object.LiquidTankAPI.isLiquidTank(block.getLocation()))
                return;

            Hopper hopper = instance.getHopperManager().getHopper(block);

            Level level = hopper.getLevel();

            if (level.getLevel() > 1) {
                event.setCancelled(true);
                ItemStack item = instance.newHopperItem(level);

                event.getBlock().setType(Material.AIR);
                event.getBlock().getLocation().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
            }

            for (ItemStack m : hopper.getFilter().getWhiteList()) {
                if (m != null)
                    event.getBlock().getLocation().getWorld().dropItemNaturally(event.getBlock().getLocation(), m);
            }

            for (ItemStack m : hopper.getFilter().getBlackList()) {
                if (m != null)
                    event.getBlock().getLocation().getWorld().dropItemNaturally(event.getBlock().getLocation(), m);
            }
            for (ItemStack m : hopper.getFilter().getVoidList()) {
                if (m != null)
                    event.getBlock().getLocation().getWorld().dropItemNaturally(event.getBlock().getLocation(), m);
            }
            instance.getHopperManager().removeHopper(block.getLocation());

            instance.getPlayerDataManager().getPlayerData(player).setSyncType(null);
    }

    private void handleSyncTouch(BlockBreakEvent event) {
        if (!Methods.isSync(event.getPlayer())) return;

        ItemStack tool = event.getPlayer().getInventory().getItemInHand();
        ItemMeta meta = tool.getItemMeta();
        if (tool.getItemMeta().getLore().size() != 2) return;

        Location location = Methods.unserializeLocation(meta.getLore().get(1).replaceAll("§", ""));

        if (location.getBlock().getType() != Material.CHEST) return;

        if (event.getBlock().getType().name().contains("SHULKER")
                || (instance.isServerVersionAtLeast(ServerVersion.V1_13) ? event.getBlock().getType() == Material.SPAWNER : event.getBlock().getType() == Material.valueOf("MOB_SPAWNER"))
                || event.getBlock().getType() == Material.HOPPER
                || event.getBlock().getType() == Material.DISPENSER) {
            return;
        }

        InventoryHolder ih = (InventoryHolder) location.getBlock().getState();
        if (event.getPlayer().getInventory().getItemInHand().getItemMeta().hasEnchant(Enchantment.SILK_TOUCH)) {
            ih.getInventory().addItem(new ItemStack(event.getBlock().getType(), 1, event.getBlock().getData()));
        } else {
            for (ItemStack is : event.getBlock().getDrops())
                ih.getInventory().addItem(is);
        }
        if (instance.isServerVersionAtLeast(ServerVersion.V1_12)) {
            event.setDropItems(false);
            return;
        }

        event.isCancelled();
        event.getPlayer().getItemInHand().setDurability((short) (event.getPlayer().getItemInHand().getDurability() + 1));
        if (event.getPlayer().getItemInHand().getDurability() >= event.getPlayer().getItemInHand().getType().getMaxDurability()) {
            event.getPlayer().getItemInHand().setType(null);
        }
        if (event.getExpToDrop() > 0)
            event.getPlayer().getWorld().spawn(event.getBlock().getLocation(), ExperienceOrb.class).setExperience(event.getExpToDrop());
        event.getBlock().setType(Material.AIR);

    }
}