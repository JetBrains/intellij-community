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
import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaSpinnerBorder implements Border, UIResource, ErrorBorderCapable {

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    JSpinner spinner = (JSpinner)c;
    JFormattedTextField editor = UIUtil.findComponentOfType(spinner, JFormattedTextField.class);
    int x1 = x + JBUI.scale(1);
    int y1 = y + JBUI.scale(3);
    int width1 = width - JBUI.scale(2);
    int height1 = height - JBUI.scale(6);
    boolean focused = c.isEnabled() && c.isVisible() && editor != null && editor.hasFocus();

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      if (c.isOpaque()) {
        g2.setColor(UIUtil.getPanelBackground());
        g2.fillRect(x, y, width, height);
      }

      g2.setColor(UIUtil.getTextFieldBackground());
      g2.fillRoundRect(x1, y1, width1, height1, JBUI.scale(5), JBUI.scale(5));
      g2.setColor(UIManager.getColor(spinner.isEnabled() ? "Spinner.darcula.enabledButtonColor" : "Spinner.darcula.disabledButtonColor"));
      if (editor != null) {
        int off = editor.getBounds().x + editor.getWidth() + ((JSpinner)c).getInsets().left + JBUI.scale(1);
        Area rect = new Area(new RoundRectangle2D.Double(x1, y1, width1, height1, JBUI.scale(5), JBUI.scale(5)));
        Area blueRect = new Area(new Rectangle(off, y1, JBUI.scale(22), height1));
        rect.intersect(blueRect);
        g2.fill(rect);

        if (UIUtil.isUnderDarcula()) {
          g2.setColor(Gray._100);
          g2.drawLine(off, y1, off, height1 + JBUI.scale(2));
        }
      }

      if (!c.isEnabled()) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
      }

      Object op = ((JComponent)c).getClientProperty("JComponent.outline");
      if (op != null) {
        g2.translate(x, y + JBUI.scale(1));
        DarculaUIUtil.paintOutlineBorder(g2, width, height - JBUI.scale(1)*2, JBUI.scale(5), true, focused,
                                         DarculaUIUtil.Outline.valueOf(op.toString()));
      } else if (focused) {
        DarculaUIUtil.paintFocusRing(g2, new Rectangle(x1 + JBUI.scale(2), y1, width1 - JBUI.scale(3), height1));
      } else {
        g2.setColor(new JBColor(Gray._149,Gray._100));
        g2.drawRoundRect(x1, y1, width1, height1, JBUI.scale(5), JBUI.scale(5));
      }
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(5, 7, 5, 7).asUIResource();
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
