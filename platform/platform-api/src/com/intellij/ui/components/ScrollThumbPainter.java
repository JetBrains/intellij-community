// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.ui.MixedColorProducer;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.RegionPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Graphics2D;

import static com.intellij.openapi.util.SystemInfo.isMac;

final class ScrollThumbPainter implements RegionPainter<Float> {
  private final MixedColorProducer fillProducer;
  private final MixedColorProducer drawProducer;

  ScrollThumbPainter(@NotNull Color fill, @NotNull Color fillHovered, @NotNull Color draw, @NotNull Color drawHovered) {
    fillProducer = new MixedColorProducer(fill, fillHovered);
    drawProducer = new MixedColorProducer(draw, drawHovered);
  }

  @Override
  public void paint(Graphics2D g, int x, int y, int width, int height, @Nullable Float value) {
    double mixer = value == null ? 0 : value.doubleValue();
    fillProducer.setMixer(mixer);
    drawProducer.setMixer(mixer);

    Color fill = fillProducer.produce();
    Color draw = drawProducer.produce();
    if (fill.getRGB() == draw.getRGB()) draw = null; // without border

    int arc = 0;
    if (isMac) {
      int margin = draw == null ? 2 : 1;
      x += margin;
      y += margin;
      width -= margin + margin;
      height -= margin + margin;
      arc = Math.min(width, height);
    }
    RectanglePainter.paint(g, x, y, width, height, arc, fill, draw);
  }
}
