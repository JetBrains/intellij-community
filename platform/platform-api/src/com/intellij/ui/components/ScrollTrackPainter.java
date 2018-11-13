// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.RegionPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Graphics2D;

final class ScrollTrackPainter implements RegionPainter<Float> {
  private final Color fill;

  ScrollTrackPainter(@NotNull Color fillHovered) {
    fill = fillHovered;
  }

  @Override
  public void paint(Graphics2D g, int x, int y, int width, int height, @Nullable Float value) {
    if (value != null) {
      int argb = fill.getRGB();
      int alpha = (int)Math.round(value.doubleValue() * (0xFF & (argb >> 24)));
      if (alpha > 0) {
        //noinspection UseJBColor
        g.setPaint(new Color((0x00FFFFFF & argb) | (alpha << 24), true));
        RectanglePainter.FILL.paint(g, x, y, width, height, null);
      }
    }
  }
}
