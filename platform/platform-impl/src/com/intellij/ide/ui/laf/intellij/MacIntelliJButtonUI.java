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

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJButtonUI extends DarculaButtonUI {
  private static Rectangle viewRect = new Rectangle();
  private static Rectangle textRect = new Rectangle();
  private static Rectangle iconRect = new Rectangle();

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
      FontMetrics fm = SwingUtilities2.getFontMetrics(b, g);
      String text = isSquare(c) ? layout(b, "...", null, fm, b.getWidth(), b.getHeight()) :
                                  layout(b, b.getText(), b.getIcon(), fm, b.getWidth(), b.getHeight());

      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.translate(0, 0);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        double lw = UIUtil.isRetina(g2) ? 0.5 : 1.0;
        Insets i = c.getBorder().getBorderInsets(c); // ComboButton adds arrow width to the insets, so take the bare border.

        // Draw background
        Shape outerRect = new RoundRectangle2D.Double(i.left, i.top, w - (i.left + i.right), h - (i.top + i.bottom),
                                                      ARC_SIZE, ARC_SIZE);
        Paint p;
        if (isDefaultButton(c)) {
          p = IntelliJLaf.isGraphite() ?
              new GradientPaint(w/2, i.top, new Color(0xb2b2b7), w/2, h - (i.top + i.bottom), new Color(0x929297)) :
              new GradientPaint(w/2, i.top, new Color(0x84b0f7), w/2, h - (i.top + i.bottom), new Color(0x4b80fb));
        } else if (b.isEnabled()) {
          p = Gray.xFF;
        } else {
          p = Gray.xF1;
        }

        g2.setPaint(p);
        g2.fill(outerRect);

        // Draw  outline
        Path2D outline = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        outline.append(outerRect, false);
        outline.append(new RoundRectangle2D.Double(i.left + lw, i.top + lw,
                                                   w - lw*2 - (i.left + i.right),
                                                   h - lw*2 - (i.top + i.bottom),
                                                   ARC_SIZE - lw, ARC_SIZE - lw), false);

        if (isDefaultButton(c)) {
          p = IntelliJLaf.isGraphite() ?
              new GradientPaint(w/2, i.top, new Color(0xa5a5ab), w/2, h - (i.top + i.bottom), new Color(0x7d7d83)) :
              new GradientPaint(w/2, i.top, new Color(0x6d9ff6), w/2, h - (i.top + i.bottom), new Color(0x3861fa));
        } else if (b.isEnabled()) {
          p = new GradientPaint(w/2, i.top, Gray.xC9, w/2, h - (i.top + i.bottom), Gray.xAC);
        } else {
          p = new GradientPaint(w/2, i.top, Gray.xD2, w/2, h - (i.top + i.bottom), Gray.xC3);
        }

        g2.setPaint(p);
        g2.fill(outline);
      } finally {
        g2.dispose();
      }

      if (isSquare(c)) {
        UISettings.setupAntialiasing(g);
        paintText(g, b, textRect, text);
      } else {
        // Paint the Icon
        if(b.getIcon() != null) {
          paintIcon(g,c,iconRect);
        }

        if (text != null && !text.isEmpty()){
          View v = (View) c.getClientProperty(BasicHTML.propertyKey);
          if (v != null) {
            v.paint(g, textRect);
          } else {
            UISettings.setupAntialiasing(g);
            paintText(g, b, textRect, text);
          }
        }
      }
    }
  }

  private static boolean isComboButton(JComponent c) {
    return c instanceof AbstractButton && c.getClientProperty("styleCombo") == Boolean.TRUE;
  }

  private String layout(AbstractButton b, String text, Icon icon, FontMetrics fm, int width, int height) {
    Insets i = b.getInsets();
    viewRect.x = i.left;
    viewRect.y = i.top;
    viewRect.width = width - (i.right + viewRect.x);
    viewRect.height = height - (i.bottom + viewRect.y);

    textRect.x = textRect.y = textRect.width = textRect.height = 0;
    iconRect.x = iconRect.y = iconRect.width = iconRect.height = 0;

    if (isComboButton(b)) {
      viewRect.x += 6;
    }

    // layout the text and icon
    return SwingUtilities.layoutCompoundLabel(
      b, fm, text, icon,
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect, text == null ? 0 : b.getIconTextGap());
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    if (isHelpButton(c)) {
      Icon icon = MacIntelliJIconCache.getIcon("helpButton", false, false);
      return new Dimension(icon.getIconWidth(), icon.getIconHeight());
    } else if (c.getBorder() instanceof MacIntelliJButtonBorder || isComboButton(c)) {
      return new Dimension(size.width + (isComboButton(c) ? 10 : 18), 27);
    }
    return size;
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
