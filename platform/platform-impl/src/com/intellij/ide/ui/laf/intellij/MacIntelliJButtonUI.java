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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJButtonUI extends DarculaButtonUI {
  static final int ARC_SIZE = JBUI.scale(6);

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJButtonUI();
  }

  @SuppressWarnings("UseJBColor")
  @Override
  public void paint(Graphics g, JComponent c) {
    if (!(c.getBorder() instanceof MacIntelliJButtonBorder) && !isComboButton(c)) {
      super.paint(g, c);
      return;
    }
    int w = c.getWidth();
    int h = c.getHeight();
    if (isHelpButton(c)) {
      Icon icon = MacIntelliJIconCache.getIcon("helpButton", false, c.hasFocus());
      int x = (w - icon.getIconWidth()) / 2;
      int y = (h - icon.getIconHeight()) / 2;
      icon.paintIcon(c, g, x, y);
    } else {
      AbstractButton b = (AbstractButton) c;
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.translate(0, 0);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        float lw = UIUtil.isRetina(g2) ? 0.5f : 1.0f;
        Insets i = c.getBorder().getBorderInsets(c); // ComboButton adds arrow width to the insets, so take the bare border.

        // Draw background
        Shape outerRect = new RoundRectangle2D.Float(i.left, i.top, w - (i.left + i.right), h - (i.top + i.bottom),
                                                      ARC_SIZE, ARC_SIZE);
        Paint p;
        if (!b.isEnabled()) {
          p = Gray.xF1;
        } else if (isDefaultButton(c)) {
          p = IntelliJLaf.isGraphite() ?
              new GradientPaint(w/2, i.top, new Color(0xb2b2b7), w/2, h - (i.top + i.bottom), new Color(0x929297)) :
              new GradientPaint(w/2, i.top, new Color(0x68b2fa), w/2, h - (i.top + i.bottom), new Color(0x0e80ff));
        } else {
          p = Gray.xFF;
        }

        g2.setPaint(p);
        g2.fill(outerRect);

        // Draw  outline
        Path2D outline = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        outline.append(outerRect, false);
        outline.append(new RoundRectangle2D.Float(i.left + lw, i.top + lw,
                                                   w - lw*2 - (i.left + i.right),
                                                   h - lw*2 - (i.top + i.bottom),
                                                   ARC_SIZE - lw, ARC_SIZE - lw), false);

        if (!b.isEnabled()) {
          p = new GradientPaint(w/2, i.top, Gray.xD2, w/2, h - (i.top + i.bottom), Gray.xC3);
        } else if (isDefaultButton(c)) {
          p = IntelliJLaf.isGraphite() ?
              new GradientPaint(w/2, i.top, new Color(0xa5a5ab), w/2, h - (i.top + i.bottom), new Color(0x7d7d83)) :
              new GradientPaint(w/2, i.top, new Color(0x4ba0f8), w/2, h - (i.top + i.bottom), new Color(0x095eff));
        } else {
          p = new GradientPaint(w/2, i.top, Gray.xC9, w/2, h - (i.top + i.bottom), Gray.xAC);
        }

        g2.setPaint(p);
        g2.fill(outline);

        paintContents(g2, b);
      } finally {
        g2.dispose();
      }
    }
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    if (isHelpButton(c)) {
      Icon icon = MacIntelliJIconCache.getIcon("helpButton", false, false);
      return new Dimension(icon.getIconWidth(), icon.getIconHeight());
    } else if (c.getBorder() instanceof MacIntelliJButtonBorder || isComboButton(c)) {
      return JBUI.size(size.width + (isComboButton(c) ? 10 : 18), 27);
    }
    return JBUI.size(size);
  }

  @Override
  protected void paintDisabledText(Graphics g, String text, JComponent c, Rectangle textRect, FontMetrics metrics) {
    int x = textRect.x + getTextShiftOffset();
    int y = textRect.y + metrics.getAscent() + getTextShiftOffset();
    if (isDefaultButton(c)) {
     g.setColor(Gray.xCC);
    } else {
      g.setColor(UIManager.getColor("Button.disabledText"));
    }
    SwingUtilities2.drawStringUnderlineCharAt(c, g, text, -1, x, y);
  }
}
