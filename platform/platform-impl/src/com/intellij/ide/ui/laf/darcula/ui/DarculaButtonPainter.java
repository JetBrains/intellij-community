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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaButtonPainter implements Border, UIResource {
  private static final int myOffset = 4;

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final Graphics2D g2d = (Graphics2D)g;
    final Insets ins = getBorderInsets(c);
    final int yOff = (ins.top + ins.bottom) / 4;
    int offset = getOffset();
    if (c.hasFocus()) {
      DarculaUIUtil.paintFocusRing(g2d, offset, yOff, width - 2 * offset, height - 2 * yOff);
    } else {
      final GraphicsConfig config = new GraphicsConfig(g);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
      g2d.setPaint(
        UIUtil.getGradientPaint(width / 2, y + yOff + 1, Gray._80.withAlpha(90), width / 2, height - 2 * yOff, Gray._90.withAlpha(90)));
      //g.drawRoundRect(x + offset + 1, y + yOff + 1, width - 2 * offset, height - 2*yOff, 5, 5);

      ((Graphics2D)g).setPaint(Gray._100.withAlpha(180));
      g.drawRoundRect(x + offset, y + yOff, width - 2 * offset, height - 2*yOff, 5, 5);

      config.restore();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return new InsetsUIResource(8, 16, 8, 14);
  }

  protected int getOffset() {
    return myOffset;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
