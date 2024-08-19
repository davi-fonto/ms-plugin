package com.ms.plugins;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

public class App extends JavaPlugin implements Listener {
    private Map<UUID, Integer> warnings = new HashMap<>();

    @Override
    public void onEnable() {
        Server server = getServer();
        ConsoleCommandSender consoleSender = Bukkit.getConsoleSender();
        server.getPluginManager().registerEvents(this, this);
        ItemStack craftingStick = new ItemStack(Material.STICK);
        ItemMeta craftingStickMeta = craftingStick.getItemMeta();
        ItemStack shulkerStick = new ItemStack(Material.SHULKER_BOX);
        ItemMeta shulkerStickMeta = shulkerStick.getItemMeta();

        if (craftingStickMeta != null) {
            craftingStickMeta.setItemName("crafting_stick");
            craftingStickMeta.setDisplayName("§6Crafting stick");
            craftingStick.setItemMeta(craftingStickMeta);
        }
        if (shulkerStickMeta != null) {
            shulkerStickMeta.setItemName("shulker_stick");
            shulkerStickMeta.setDisplayName("§6Shulker stick");
            shulkerStick.setItemMeta(shulkerStickMeta);
        }
        server.addRecipe(new ShapedRecipe(new NamespacedKey(this, "crafting_stick"), craftingStick)
                .shape("  C", " S ").setIngredient('C', Material.CRAFTING_TABLE).setIngredient('S', Material.STICK));
        server.addRecipe(new ShapedRecipe(new NamespacedKey(this, "shulker_stick"), shulkerStick)
                .shape("  B", " S ").setIngredient('B', Material.SHULKER_BOX).setIngredient('S', Material.STICK));
        server.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (World world : server.getWorlds())
                if (world.getName().startsWith("hardcore"))
                    for (Player player : world.getPlayers()) {
                        Location location = player.getLocation();
                        PlayerInventory inventory = player.getInventory();
                        UUID playerId = player.getUniqueId();
                        if (location.getBlock().getLightLevel() < 2 && location.getY() < -40
                                && !player.hasPotionEffect(PotionEffectType.NIGHT_VISION)
                                && !player.hasPotionEffect(PotionEffectType.GLOWING)
                                && inventory.getItemInMainHand().getType() != Material.TORCH
                                && inventory.getItemInOffHand().getType() != Material.TORCH)
                            if (warnings.containsKey(playerId)) {
                                int count = warnings.get(playerId) + 1;
                                if (count == 5) {
                                    server.dispatchCommand(consoleSender,
                                            "warn " + player.getName() + " utilizzo della fullbright in hardcore");
                                    warnings.remove(playerId);
                                } else
                                    warnings.put(playerId, count);
                            } else
                                warnings.put(playerId, 1);
                        else if (warnings.containsKey(playerId)) {
                            int count = warnings.get(playerId) - 1;
                            if (count <= 0)
                                warnings.remove(playerId);
                            else
                                warnings.put(playerId, count);
                        }
                    }
        }, 200L, 200L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && item.hasItemMeta()) {
            String itemName = item.getItemMeta().getItemName();
            if (itemName.equals("crafting_stick"))
                player.openWorkbench(null, true);
            else if (itemName.equals("shulker_stick")) {
                event.getPlayer().openInventory(
                        ((ShulkerBox) ((BlockStateMeta) item.getItemMeta()).getBlockState()).getInventory());
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        int index = matrix.length / 4;

        if (matrix[index] != null && matrix[index].getType() == Material.SHULKER_BOX && matrix[index].hasItemMeta()) {
            ItemMeta blockStateMeta = matrix[index].getItemMeta();
            if (blockStateMeta instanceof BlockStateMeta) {
                BlockState shulkerBox = ((BlockStateMeta) blockStateMeta).getBlockState();
                if (shulkerBox instanceof ShulkerBox) {
                    ItemStack result = inventory.getResult();
                    if (result != null && result.getItemMeta().getItemName().equals("shulker_stick")) {
                        ItemStack newShulkerStick = result.clone();
                        BlockStateMeta newMeta = (BlockStateMeta) newShulkerStick.getItemMeta();
                        newMeta.setBlockState(shulkerBox);
                        shulkerBox.update();
                        newShulkerStick.setItemMeta(newMeta);
                        inventory.setResult(newShulkerStick);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        Entity player = event.getEntity();
        if (!(player instanceof Player) || !player.getWorld().getName().startsWith("hardcore_"))
            return;
        RegainReason reason = event.getRegainReason();

        if (reason == RegainReason.SATIATED || reason == RegainReason.REGEN) {
            double health = ((Player) player).getHealth();
            double midHealth = ((Player) player)
                    .getAttribute(Attribute.GENERIC_MAX_HEALTH)
                    .getDefaultValue() / 2;
            if (health >= midHealth)
                event.setCancelled(true);
            else
                event.setAmount(Math.min(event.getAmount() / 2, midHealth - health));
        }
    }
}