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

import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaSpinnerBorder implements Border, UIResource, ErrorBorderCapable {

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      //JBInsets.removeFrom(r, JBUI.insets(1, 0));
      g2.translate(x, y);

      float lw = lw(g2);
      float bw = bw();
      float arc = arc();

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(new RoundRectangle2D.Float(bw, bw, width - bw * 2, height - bw * 2, arc, arc), false);
      border.append(new RoundRectangle2D.Float(bw + lw, bw + lw, width - (bw + lw) * 2, height - (bw + lw) * 2, arc, arc), false);

      g2.setColor(getOutlineColor(c.isEnabled()));
      g2.fill(border);

      Object op = ((JComponent)c).getClientProperty("JComponent.outline");
      if (op != null) {
        paintOutlineBorder(g2, width, height, arc, true, isFocused(c),
                                         Outline.valueOf(op.toString()));
      } else if (isFocused(c)) {
        paintFocusBorder(g2, width, height, arc, true);
      }
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3, 8, 3, 3).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }

  public static boolean isFocused(Component c) {
    if (c.hasFocus()) return true;

    if (c instanceof JSpinner) {
      JSpinner spinner = (JSpinner)c;
      if (spinner.getEditor() != null) {
        synchronized (spinner.getEditor().getTreeLock()) {
          return spinner.getEditor().getComponent(0).hasFocus();
        }
      }
    }
    return false;
  }
}
