/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.paint;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.WavePainter;

import java.awt.*;
import java.awt.geom.*;

/**
 * @author Sergey.Malenkov
 */
public enum EffectPainter implements RegionPainter<Paint> {
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#LINE_UNDERSCORE
   */
  LINE_UNDERSCORE {
    /**
     * Draws a horizontal line under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param paint  optional color patterns
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Paint paint) {
      if (!Registry.is("ide.text.effect.new")) {
        if (paint != null) g.setPaint(paint);
        g.drawLine(x, y + 1, x + width, y + 1);
      }
      else if (width > 0 && height > 0) {
        if (paint != null) g.setPaint(paint);
        drawLineUnderscore(g, x, y, width, height, 1, this);
      }
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#BOLD_LINE_UNDERSCORE
   */
  BOLD_LINE_UNDERSCORE {
    /**
     * Draws a bold horizontal line under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param paint  optional color patterns
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Paint paint) {
      if (!Registry.is("ide.text.effect.new")) {
        if (paint != null) g.setPaint(paint);
        int h = JBUI.scale(Registry.intValue("editor.bold.underline.height", 2));
        g.fillRect(x, y, width, h);
      }
      else if (width > 0 && height > 0) {
        if (paint != null) g.setPaint(paint);
        drawLineUnderscore(g, x, y, width, height, 2, this);
      }
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#BOLD_DOTTED_LINE
   */
  BOLD_DOTTED_UNDERSCORE {
    /**
     * Draws a bold horizontal line of dots under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param paint  optional color patterns
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Paint paint) {
      if (!Registry.is("ide.text.effect.new")) {
        UIUtil.drawBoldDottedLine(g, x, x + width, SystemInfo.isMac ? y : y + 1, g.getColor(), (Color)paint, false);
      }
      else if (width > 0 && height > 0) {
        if (paint != null) g.setPaint(paint);
        drawLineUnderscore(g, x, y, width, height, 2, this);
      }
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#WAVE_UNDERSCORE
   */
  WAVE_UNDERSCORE {
    private final BasicStroke STROKE = new BasicStroke(.7f);

    /**
     * Draws a horizontal wave under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param paint  optional color patterns
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Paint paint) {
      if (!Registry.is("ide.text.effect.new")) {
        if (paint != null) g.setPaint(paint);
        WavePainter.forColor(g.getColor()).paint(g, x, x + width, y + height);
      }
      else if (width > 0 && height > 0) {
        g = (Graphics2D)g.create(x, y, width, height);
        g.clipRect(0, 0, width, height);
        if (paint != null) g.setPaint(paint);
        int h = getMaxHeight(height);
        int length = 2 * h - 2; // the spatial period of the wave

        double dx = -((x % length + length) % length); // normalize
        double upper = height - h;
        double lower = height - 1;
        Path2D path = new Path2D.Double();
        path.moveTo(dx, lower);
        if (height < 6) {
          g.setStroke(STROKE);
          double size = (double)length / 2;
          while (true) {
            path.lineTo(dx += size, upper);
            if (dx > width) break;
            path.lineTo(dx += size, lower);
            if (dx > width) break;
          }
        }
        else {
          double size = (double)length / 4;
          double prev = dx - size / 2;
          double center = (upper + lower) / 2;
          while (true) {
            path.quadTo(prev += size, lower, dx += size, center);
            if (dx > width) break;
            path.quadTo(prev += size, upper, dx += size, upper);
            if (dx > width) break;
            path.quadTo(prev += size, upper, dx += size, center);
            if (dx > width) break;
            path.quadTo(prev += size, lower, dx += size, lower);
            if (dx > width) break;
          }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.draw(path);
        g.dispose();
      }
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#STRIKEOUT
   */
  STRIKE_THROUGH {
    /**
     * Draws a horizontal line through a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height text height
     * @param paint  optional color patterns
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Paint paint) {
      if (width > 0 && height > 0) {
        if (paint != null) g.setPaint(paint);
        drawLineCentered(g, x, y - height, width, height, 1, this);
      }
    }
  };

  private static int getMaxHeight(int height) {
    return height > 7 && Registry.is("ide.text.effect.new.scale") ? height >> 1 : 3;
  }

  private static void drawLineUnderscore(Graphics2D g, int x, int y, int width, int height, int thickness, EffectPainter painter) {
    if (height > 3) {
      int max = getMaxHeight(height);
      y += height - max;
      height = max;
      if (thickness > 1 && height > 3) {
        thickness = JBUI.scale(thickness);
      }
    }
    drawLineCentered(g, x, y, width, height, thickness, painter);
  }

  private static void drawLineCentered(Graphics2D g, int x, int y, int width, int height, int thickness, EffectPainter painter) {
    int offset = height - thickness;
    if (offset > 0) {
      y += offset - (offset >> 1);
      height = thickness;
    }
    if (painter == BOLD_DOTTED_UNDERSCORE) {
      int length = 2 * height; // the spatial period

      int dx = -((x % length + length) % length); // normalize
      if (-dx >= height) {
        dx += length;
      }
      Integer round = height > 2 ? height : null;
      while (dx <= width) {
        RectanglePainter.FILL.paint(g, x + dx, y, height, height, round);
        dx += length;
      }
    }
    else {
      g.fillRect(x, y, width, height);
    }
  }
}
