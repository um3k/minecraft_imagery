package dev.jensderuiter.minecraft_imagery;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.awt.*;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class ImageCapture {

    Location location;
    BufferedImage image;
    Graphics2D graphics;

    List<Player> entities;

    Map<Player, List<Point2D>> playerOccurrences;

    public ImageCapture(
            Location location,
            List<Player> entities
    ) {
        this.location = location.clone();
        this.image = new BufferedImage(Constants.MAP_WIDTH, Constants.MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        this.graphics = this.image.createGraphics();
        this.playerOccurrences = new HashMap<>();
        this.entities = entities;
    }

    public BufferedImage render() {

        long startTime = System.currentTimeMillis();

        // get pitch and yaw of players head to calculate ray trace directions
        double pitch = -Math.toRadians(this.location.getPitch());
        double yaw = Math.toRadians(this.location.getYaw() + 90);



        // loop through every pixel on map
        for (int x = 0; x < Constants.MAP_WIDTH; x++) {
            for (int y = 0; y < Constants.MAP_HEIGHT; y++) {

                // calculate ray rotations
                double yrotate = -((y) * .9 / Constants.MAP_HEIGHT - .45);
                double xrotate = ((x) * .9 / Constants.MAP_WIDTH - .45);

                Vector rayTraceVector = new Vector(Math.cos(yaw + xrotate) * Math.cos(pitch + yrotate),
                        Math.sin(pitch + yrotate), Math.sin(yaw + xrotate) * Math.cos(pitch + yrotate));

                RayTraceResult entityResult = this.rayTraceEntitiesFromList(this.location, rayTraceVector, 64);

                Location lookFrom = this.location.clone();
                double[] dye = new double[]{1, 1, 1};

                // max tries for blocks to look through
                for (int i = 0; i < 20; i++) {
                    RayTraceResult result = this.location.getWorld().rayTraceBlocks(
                            lookFrom, rayTraceVector, 256,
                            FluidCollisionMode.ALWAYS, false);

                    if (result == null) {
                        // no block was hit, so we will assume we are looking at the sky
                        this.image.setRGB(x, y, Constants.SKY_COLOR.getRGB());
                        break;
                    }

                    if (Constants.EXCLUDED_BLOCKS.contains(result.getHitBlock().getType())) {
                        // we hit an excluded block. update position and keep looking
                        lookFrom = result.getHitPosition().toLocation(this.location.getWorld());
                        continue;
                    }

                    SeeThroughBlock seeThroughBlock = Constants.THROUGH_BLOCKS.get(result.getHitBlock().getType());

                    if (seeThroughBlock != null) {
                        // we hit a see-through block. update dye, update position and keep looking
                        dye = Util.applyToDye(dye, seeThroughBlock.dye);
                        lookFrom = result.getHitPosition().toLocation(this.location.getWorld());
                        continue;
                    }

                    // color the pixel
                    this.colorWithDye(x, y, result, dye, rayTraceVector);
                    break;
                }

                if (entityResult == null) continue;

                Entity hitEntity = entityResult.getHitEntity();
                if (hitEntity instanceof Player hitPlayer) {
                    List<Point2D> pixelList = playerOccurrences.get(hitPlayer);
                    if (pixelList == null) {
                        pixelList = new ArrayList<>();
                    }
                    pixelList.add(new Point2D.Float(x, y));
                    playerOccurrences.put(hitPlayer, pixelList);
                }
            }
        }

        for (Map.Entry<Player, List<Point2D>> playerEntry : playerOccurrences.entrySet()) {
            Point2D topLeftPoint = playerEntry.getValue().get(0);
            Point2D bottomRightPoint = playerEntry.getValue().get(playerEntry.getValue().size() - 1);

            int width = (int) (bottomRightPoint.getX() - topLeftPoint.getX() + 1);
            int height = (int) (bottomRightPoint.getY() - topLeftPoint.getY() + 1);

            Player player = playerEntry.getKey();

            float cameraYaw = this.location.getYaw() + 180;
            float playerYaw = player.getLocation().getYaw() + 180;

            boolean isToFront = !(cameraYaw - 90 < playerYaw && cameraYaw + 90 > playerYaw);

            BufferedImage combinedTexture = isToFront ? Util.getPlayerSkinFront(player) : Util.getPlayerSkinBack(player);

            graphics.drawImage(
                    combinedTexture,
                    (int) topLeftPoint.getX(),
                    (int) topLeftPoint.getY(),
                    width,
                    height,
                    null
            );

        }

        Bukkit.getLogger().info("This took " + (System.currentTimeMillis() - startTime) + "ms");
        return image;
    }

    private void colorWithDye(int x, int y, RayTraceResult result, double[] dye, Vector rayTraceVector) {
        byte lightLevel = result.getHitBlock().getRelative(result.getHitBlockFace()).getLightLevel();

        if(lightLevel > 0) {
            double shadowLevel = 15.0;

            for(int i = 0; i < dye.length; i++) {
                dye[i] = dye[i] * (lightLevel / shadowLevel);
            }
        }

        Color color = Util.colorFromType(result.getHitBlock(), dye);

        if (color != null) this.image.setRGB(x, y, color.getRGB());
    }

    private RayTraceResult rayTraceEntitiesFromList(Location start, Vector direction, double maxDistance) {
        Vector startPos = start.toVector();
        Entity nearestHitEntity = null;
        RayTraceResult nearestHitResult = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        Iterator var17 = entities.iterator();

        while(var17.hasNext()) {
            Entity entity = (Entity)var17.next();
            if (Util.isWithinBlockIgnoreY(entity.getLocation(), this.location)) continue;
            BoundingBox boundingBox = entity.getBoundingBox();
            RayTraceResult hitResult = boundingBox.rayTrace(startPos, direction, maxDistance);
            if (hitResult != null) {
                double distanceSq = startPos.distanceSquared(hitResult.getHitPosition());
                if (distanceSq < nearestDistanceSq) {
                    nearestHitEntity = entity;
                    nearestHitResult = hitResult;
                    nearestDistanceSq = distanceSq;
                }
            }
        }

        return nearestHitEntity == null ? null : new RayTraceResult(nearestHitResult.getHitPosition(), nearestHitEntity, nearestHitResult.getHitBlockFace());
    }

}
