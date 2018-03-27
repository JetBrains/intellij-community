/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaButtonPainter implements Border, UIResource {
  private static final int myOffset = 4;

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      if (DarculaButtonUI.isSquare(c)) {

        Rectangle r = new Rectangle(width, height);
        //JBInsets.removeFrom(r, JBUI.insets(1));
        g2.translate(r.x, r.y);

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        float lw = DarculaUIUtil.lw(g2);
        float bw = DarculaUIUtil.bw();
        float arc = JBUI.scale(2.0f);
        border.append(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc), false);
        border.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2, arc - lw, arc - lw),
                      false);

        g2.setColor(DarculaUIUtil.getOutlineColor(c.isEnabled()));
        g2.fill(border);

        if (c.hasFocus()) {
          DarculaUIUtil.paintFocusBorder(g2, r.width, r.height, arc, true);
        }
      } else {
        int diam = JBUI.scale(22);
        if (c.hasFocus()) {
          int off = JBUI.scale(2);
          if (DarculaButtonUI.isHelpButton((JComponent)c)) {
            g2.translate(off, (height - diam) / 2.0 - off);
            DarculaUIUtil.paintFocusBorder(g2, width - off * 2, diam + off * 2, diam / 2.0f + 2.0f * off , true);
          }
          else {
            g2.translate(off, off);
            DarculaUIUtil.paintFocusBorder(g2, width - off * 2, height - off * 2, DarculaUIUtil.arc(), true);
          }
        }
        else {
          Insets ins = getBorderInsets(c);
          int yOff = (ins.top + ins.bottom) / 4;
          int offset = JBUI.scale(getOffset());
          int w = c.getWidth();
          int h = c.getHeight();

          g2.setPaint(getBorderColor());
          if (DarculaButtonUI.isHelpButton((JComponent)c)) {
            g2.drawOval((w - diam) / 2, (h - diam) / 2, diam, diam);
          }
          else {
            g2.translate(x, y);
            int r = JBUI.scale(5);
            g2.drawRoundRect(offset, yOff, width - 2 * offset, height - 2 * yOff, r, r);
            g2.translate(-x, -y);
          }
        }
      }
    } finally {
      g2.dispose();
    }
  }

  protected Color getBorderColor() {
    return Gray._100.withAlpha(180);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if (c.getParent() instanceof ActionToolbar) {
      return JBUI.insets(4, 16, 4, 16);
    }
    if (DarculaButtonUI.isSquare(c)) {
      return JBUI.insets(3).asUIResource();
    }
    return JBUI.insets(8, 16, 8, 14).asUIResource();
  }

  protected int getOffset() {
    return myOffset;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
