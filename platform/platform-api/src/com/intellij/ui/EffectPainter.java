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
package com.intellij.ui;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.RegionPainter;
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
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Paint paint) {
      // we assume here that Y is a baseline of a text
      if (!Registry.is("ide.text.effect.line.new")) {
        g.setPaint(paint);
        g.drawLine(x, y + 1, x + width, y + 1);
      }
      else if (paint != null && width > 0 && height > 0) {
        int h = height > 6 && Registry.is("ide.text.effect.wave.new.scale") ? height >> 1 : 3;
        y += height - 1 - h / 2;
        g.setPaint(paint);
        g.drawLine(x, y, x + width, y);
      }
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#WAVE_UNDERSCORE
   */
  WAVE_UNDERSCORE {
    private final BasicStroke STROKE = new BasicStroke(.5f);

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Paint paint) {
      // we assume here that Y is a baseline of a text
      if (!Registry.is("ide.text.effect.wave.new")) {
        g.setPaint(paint);
        WavePainter.forColor(g.getColor()).paint(g, x, x + width, y + height);
      }
      else if (paint != null && width > 0 && height > 0) {
        g = (Graphics2D)g.create(x, y, width, height);
        g.clipRect(0, 0, width, height);
        int h = height > 6 && Registry.is("ide.text.effect.wave.new.scale") ? height >> 1 : 3;
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
        g.setPaint(paint);
        g.draw(path);
        g.dispose();
      }
    }
  }
}
