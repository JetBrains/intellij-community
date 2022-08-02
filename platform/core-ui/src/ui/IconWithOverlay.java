// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;

/**
 * @author Konstantin Bulenkov
 */
public abstract class IconWithOverlay extends LayeredIcon {
  public IconWithOverlay(@NotNull Icon main, @NotNull Icon overlay) {
    super(main, overlay);
  }

  public abstract @Nullable Shape getOverlayShape(int x, int y);

  @SuppressWarnings("GraphicsSetClipInspection")
  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Shape dontPaintHere = getOverlayShape(x, y);
    if (dontPaintHere == null) {
      super.paintIcon(c, g, x, y);
      return;
    }
    float scale = getScale();
    Icon overlay = getIcon(1);
    assert overlay != null;
    Shape clip = g.getClip();
    Area newClip = new Area(clip);
    newClip.subtract(new Area(dontPaintHere));
    ((Graphics2D)g).setClip(newClip);
    super.paintIcon(c, g, x, y);
    ((Graphics2D)g).setClip(clip);
    if (scale != 1f) {
      overlay = IconUtil.scale(overlay, null, scale);
    }
    overlay.paintIcon(c, g, x, y);
  }

}
