/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.borders;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.MacUIUtil;

import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaButtonPainter implements Border, UIResource {
  private static final JBInsets myInsets = new JBInsets(6, 12, 6, 12);
  private static final int offset = 4;

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final Graphics2D g2d = (Graphics2D)g;
    final int yOff = myInsets.height() / 4;
    if (c.hasFocus()) {
      MacUIUtil.paintFocusRing(g2d, new Color(89, 157, 231), new Rectangle(offset, yOff, width-2*offset, height - 2*yOff));
    } else {
      final GraphicsConfig config = new GraphicsConfig(g);
      g2d.setPaint(new GradientPaint(width / 2, y + yOff + 1, Gray._120, width / 2, height - 2*yOff, Gray._105));
      g.drawRoundRect(x + offset, y + yOff + 1, width - 2 * offset, height - 2*yOff, 2, 2);

      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      ((Graphics2D)g).setPaint(Gray._30);
      g.drawRoundRect(x + offset, y + yOff, width - 2 * offset, height - 2*yOff, 4, 4);

      config.restore();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return myInsets;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
