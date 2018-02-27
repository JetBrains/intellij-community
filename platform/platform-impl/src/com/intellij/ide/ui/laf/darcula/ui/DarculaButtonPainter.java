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
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.HELP_BUTTON_DIAMETER;

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

      Rectangle r = new Rectangle(x, y, width, height);
      JBInsets.removeFrom(r, JBUI.insets(1));

      g2.translate(r.x, r.y);

      int diam = JBUI.scale(HELP_BUTTON_DIAMETER);
      float arc = DarculaUIUtil.buttonArc();
      float lw = DarculaUIUtil.lw(g2);
      float bw = DarculaUIUtil.bw();

      if (c.hasFocus()) {
        if (UIUtil.isHelpButton(c)) {
          DarculaUIUtil.paintFocusOval(g2, (r.width - diam) / 2 + lw, (r.height - diam) / 2 + lw, diam - lw, diam - lw);
        } else {
          DarculaUIUtil.paintFocusBorder(g2, r.width, r.height, arc, true);
        }
      } else {
        paintShadow(g2, r);
      }

      g2.setPaint(getBorderColor(c));

      if (UIUtil.isHelpButton(c)) {
        g2.draw(new Ellipse2D.Float((r.width - diam) / 2, (r.height - diam) / 2, diam, diam));
      } else {
        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc), false);
        border.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2, arc - lw, arc - lw),
                      false);

        g2.fill(border);
      }
    } finally {
      g2.dispose();
    }
  }

  public Color getBorderColor(Component button) {
    return button.isEnabled() ?
      button.hasFocus() ?
        UIManager.getColor(DarculaButtonUI.isDefaultButton((JComponent)button) ?
         "Button.darcula.defaultFocusedBorderColor" : "Button.darcula.focusedBorderColor") :
        UIManager.getColor(button.isEnabled() && DarculaButtonUI.isDefaultButton((JComponent)button) ?
         "Button.darcula.defaultBorderColor" : "Button.darcula.borderColor")
      : UIManager.getColor("Button.darcula.disabledBorderColor");
  }

  protected void paintShadow(Graphics2D g2, Rectangle r) {
    if (UIManager.getBoolean("Button.darcula.paintShadow")) {
      g2.setColor(UIManager.getColor("Button.darcula.shadowColor"));
      Composite composite = g2.getComposite();
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

      float bw = DarculaUIUtil.bw();
      g2.fill(new Rectangle2D.Float(bw, r.height - bw, r.width - bw * 2, JBUI.scale(2)));

      g2.setComposite(composite);
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if (c.getParent() instanceof ActionToolbar) {
      return JBUI.insets(5, 14);
    } else if (DarculaButtonUI.isSquare(c)) {
      return JBUI.insets(4).asUIResource();
    } else if (UIUtil.isHelpButton(c)) {
      return JBUI.insets(3).asUIResource();
    } if (DarculaButtonUI.isComboButton((JComponent)c)) {
      return JBUI.insets(5, 10, 5, 5).asUIResource();
    } else {
      return JBUI.insets(5, 14).asUIResource();
    }
  }

  protected int getOffset() {
    return myOffset;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
