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
   * @see com.intellij.openapi.editor.markup.EffectType#WAVE_UNDERSCORE
   */
  WAVE_UNDERSCORE {
    private final BasicStroke STROKE = new BasicStroke(.7f);

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Paint paint) {
      // we assume here that Y is a baseline of a text
      if (!Registry.is("ide.text.effect.wave.new")) {
        g.setPaint(paint);
        WavePainter.forColor(g.getColor()).paint(g, x, x + width, y + height);
      }
      else if (paint != null && width > 0 && height > 0) {
        int h = height < 4 ? 2 : Registry.is("ide.text.effect.wave.new.scale") ? height >> 1 : 3;
        if (h != height) {
          y += height - h;
          height = h;
        }
        g = (Graphics2D)g.create(x, y, width, height);
        g.clipRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(paint);

        int length = 2 * height;
        boolean simple = length < 5;

        double dx = -((x % length + length) % length); // normalize
        double size = (double)length / 4;

        double upper = 0;
        double lower = height - 1;
        double center = lower / 2;

        Path2D path = new Path2D.Double();
        path.moveTo(dx, lower);
        while (true) {
          if (simple) {
            dx += size + size;
            path.lineTo(dx, upper);
            if (dx > width) break;
            dx += size + size;
            path.lineTo(dx, lower);
            if (dx > width) break;
          }
          else {
            dx += size;
            path.quadTo(dx - size / 2, lower, dx, center);
            if (dx > width) break;
            dx += size;
            path.quadTo(dx - size / 2, upper, dx, upper);
            if (dx > width) break;
            dx += size;
            path.quadTo(dx - size / 2, upper, dx, center);
            if (dx > width) break;
            dx += size;
            path.quadTo(dx - size / 2, lower, dx, lower);
            if (dx > width) break;
          }
        }
        if (simple) g.setStroke(STROKE);
        g.draw(path);
        g.dispose();
      }
    }
  }
}
