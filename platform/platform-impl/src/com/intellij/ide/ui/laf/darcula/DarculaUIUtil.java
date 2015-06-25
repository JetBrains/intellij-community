/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaUIUtil {
  public static final Color GLOW_COLOR = new JBColor(new Color(96, 132, 212), new Color(96, 175, 255));

  public static void paintFocusRing(Graphics g, int x, int y, int width, int height) {
    MacUIUtil.paintFocusRing((Graphics2D)g, getGlow(), new Rectangle(x, y, width, height));
  }

  public static void paintFocusOval(Graphics g, int x, int y, int width, int height) {
    MacUIUtil.paintFocusRing((Graphics2D)g, getGlow(), new Rectangle(x, y, width, height), true);
  }

  private static Color getGlow() {
    return new JBColor(new Color(35, 121, 212), new Color(96, 175, 255));
  }

  public static void paintSearchFocusRing(Graphics2D g, Rectangle bounds) {
    paintSearchFocusRing(g, bounds, -1);
  }
  public static void paintSearchFocusRing(Graphics2D g, Rectangle bounds, int maxArcSize) {
    int correction = UIUtil.isUnderAquaLookAndFeel() ? 30 : UIUtil.isUnderDarcula() ? 50 : 0;
    final Color[] colors = new Color[]{
      ColorUtil.toAlpha(getGlow(), 180 - correction),
      ColorUtil.toAlpha(getGlow(), 120 - correction),
      ColorUtil.toAlpha(getGlow(), 70  - correction),
      ColorUtil.toAlpha(getGlow(), 100 - correction),
      ColorUtil.toAlpha(getGlow(), 50  - correction)
    };

    final Object oldAntialiasingValue = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    final Object oldStrokeControlValue = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);


    final Rectangle r = new Rectangle(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6);
    int arcSize = r.height - 1;
    if (maxArcSize>0) arcSize = Math.min(maxArcSize, arcSize);
    if (arcSize %2 == 1) arcSize--;


    g.setColor(colors[0]);
    g.drawRoundRect(r.x + 2, r.y + 2, r.width - 5, r.height - 5, arcSize-4, arcSize-4);

    g.setColor(colors[1]);
    g.drawRoundRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3, arcSize-2, arcSize-2);

    g.setColor(colors[2]);
    g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arcSize, arcSize);


    g.setColor(colors[3]);
    g.drawRoundRect(r.x+3, r.y+3, r.width - 7, r.height - 7, arcSize-6, arcSize-6);

    g.setColor(colors[4]);
    g.drawRoundRect(r.x+4, r.y+4, r.width - 9, r.height - 9, arcSize-8, arcSize-8);

    // restore rendering hints
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasingValue);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, oldStrokeControlValue);
  }

}
