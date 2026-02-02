// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * @author Konstantin Bulenkov
 */
public abstract class IconWithOverlay extends LayeredIcon {
  public IconWithOverlay(@NotNull Icon main, @NotNull Icon overlay) {
    super(main, overlay);
  }

  public abstract @Nullable Shape getOverlayShape(int x, int y);

  public @NotNull Icon getMainIcon() {
    return getScaled(0);
  }

  public @NotNull Icon getOverlayIcon() {
    return getScaled(1);
  }

  private @NotNull Icon getScaled(int layer) {
    Icon icon = getIcon(layer);
    assert icon != null;
    float scale = getScale();
    return scale == 1f ? icon : IconUtil.scale(icon, null, scale);
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Shape dontPaintHere = getOverlayShape(0, 0);
    if (dontPaintHere == null) {
      super.paintIcon(c, g, x, y);
      return;
    }
    var iconWidth = getIconWidth();
    var iconHeight = getIconHeight();
    if (iconWidth <= 0 || iconHeight <= 0) return; // for whatever reason: icons not initialized yet, empty icons mistakenly used...
    BufferedImage img = UIUtil.createImage(((Graphics2D)g).getDeviceConfiguration(), iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.CEIL);
    Graphics2D g2 = img.createGraphics();
    getMainIcon().paintIcon(c, g2, 0, 0);
    GraphicsConfig config = new GraphicsConfig(g2);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
    g2.fill(dontPaintHere);
    config.restore();
    UIUtil.drawImage(g,img, x, y, null);
    getOverlayIcon().paintIcon(c, g, x, y);
  }
}