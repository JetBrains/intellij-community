// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Konstantin Bulenkov
 */
public abstract class IconWithOverlay extends LayeredIcon {
  public IconWithOverlay(@NotNull Icon main, @NotNull Icon overlay) {
    super(main, overlay);
  }

  public abstract @Nullable Shape getOverlayShape(int x, int y);

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Shape dontPaintHere = getOverlayShape(0, 0);
    if (dontPaintHere == null) {
      super.paintIcon(c, g, x, y);
      return;
    }
    float scale = getScale();
    BufferedImage img = UIUtil.createImage(((Graphics2D)g).getDeviceConfiguration(), getIconWidth(), getIconHeight(), BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.CEIL);
    Graphics2D g2 = img.createGraphics();
    IconUtil.scale(getIcon(0), scale).paintIcon(c, g2, 0, 0);
    GraphicsConfig config = new GraphicsConfig(g2);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
    g2.fill(dontPaintHere);
    config.restore();
    UIUtil.drawImage(g,img, x, y, null);
    Icon overlay = getIcon(1);
    assert overlay != null;
    if (scale != 1f) {
      overlay = IconUtil.scale(overlay, null, scale);
    }
    overlay.paintIcon(c, g, x, y);
  }
}