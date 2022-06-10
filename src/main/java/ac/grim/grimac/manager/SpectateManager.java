package ac.grim.grimac.manager;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.manager.init.Initable;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpectateManager implements Initable {

    private final Map<UUID, PreviousState> spectatingPlayers = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> allowedWorlds = ConcurrentHashMap.newKeySet();

    private boolean checkWorld = false;

    @Override
    public void start() {
        allowedWorlds.clear();
        allowedWorlds.addAll(GrimAPI.INSTANCE.getConfigManager().getConfig().getStringListElse("spectators.allowed-worlds", new ArrayList<>()));
        checkWorld = !(allowedWorlds.isEmpty() || new ArrayList<>(allowedWorlds).get(0).isEmpty());
    }

    public boolean isSpectating(UUID uuid) {
        return spectatingPlayers.containsKey(uuid);
    }

    public boolean shouldHidePlayer(GrimPlayer receiver, WrapperPlayServerPlayerInfo.PlayerData playerData) {
        return playerData.getUser() != null
                && !playerData.getUser().getUUID().equals(receiver.playerUUID) // don't hide to yourself
                && (spectatingPlayers.containsKey(playerData.getUser().getUUID()) || hiddenPlayers.contains(playerData.getUser().getUUID())) //hide if you are a spectator
                && !(spectatingPlayers.containsKey(receiver.playerUUID) || hiddenPlayers.contains(receiver.playerUUID)) // don't hide to other spectators
                && (!checkWorld || (receiver.bukkitPlayer != null && allowedWorlds.contains(receiver.bukkitPlayer.getWorld().getName()))); // hide if you are in a specific world
    }

    public boolean enable(Player player) {
        if (spectatingPlayers.containsKey(player.getUniqueId())) return false;
        spectatingPlayers.put(player.getUniqueId(), new PreviousState(player.getGameMode(), player.getLocation()));
        return true;
    }

    public void onLogin(Player player) {
        hiddenPlayers.add(player.getUniqueId());
    }

    public void onQuit(Player player) {
        hiddenPlayers.remove(player.getUniqueId());
        disable(player);
    }

    public void disable(Player player) {
        PreviousState previousState = spectatingPlayers.get(player.getUniqueId());
        if (previousState != null) {
            player.teleport(previousState.location);
            player.setGameMode(previousState.gameMode);
        }
        handlePlayerStopSpectating(player.getUniqueId());
    }

    public void handlePlayerStopSpectating(UUID uuid) {
        spectatingPlayers.remove(uuid);
    }

    private static class PreviousState {
        public PreviousState(org.bukkit.GameMode gameMode, Location location) {
            this.gameMode = gameMode;
            this.location = location;
        }

        private final org.bukkit.GameMode gameMode;
        private final Location location;
    }

}
