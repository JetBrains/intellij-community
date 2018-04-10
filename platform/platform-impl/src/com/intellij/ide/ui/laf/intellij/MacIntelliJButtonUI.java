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

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.ui.Gray;
import com.intellij.util.ui.*;
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

  @Override
  public void paint(Graphics g, JComponent c) {
    if (!(c.getBorder() instanceof MacIntelliJButtonBorder) && !isComboButton(c)) {
      super.paint(g, c);
      return;
    }
    int w = c.getWidth();
    int h = c.getHeight();
    if (UIUtil.isHelpButton(c)) {
      Icon icon = IconCache.getIcon("helpButton", false, c.hasFocus());
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

        float lw = getLineWidth(g2);
        Insets i = c.getInsets();

        // Draw background
        Shape outerRect = new RoundRectangle2D.Float(i.left, i.top, w - (i.left + i.right), h - (i.top + i.bottom),
                                                      ARC_SIZE, ARC_SIZE);
        g2.setPaint(getBackgroundPaint(c));
        g2.fill(outerRect);

        // Draw  outline
        Path2D outline = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        outline.append(outerRect, false);
        outline.append(new RoundRectangle2D.Float(i.left + lw, i.top + lw,
                                                   w - lw*2 - (i.left + i.right),
                                                   h - lw*2 - (i.top + i.bottom),
                                                   ARC_SIZE - lw, ARC_SIZE - lw), false);
        g2.setPaint(getBorderPaint(c));
        g2.fill(outline);

        paintContents(g2, b);
      } finally {
        g2.dispose();
      }
    }
  }

  public static float getLineWidth(Graphics2D g) {
    return UIUtil.isRetina(g) ? 0.5f : 1.0f;
  }

  @SuppressWarnings("UseJBColor")
  private static Paint getBackgroundPaint(JComponent b) {
    int h = b.getHeight();
    Insets i = b.getInsets();

    if (!b.isEnabled()) {
      return Gray.xF1;
    } else if (isDefaultButton(b)) {
      return UIUtil.isGraphite() ?
          new GradientPaint(0, i.top, new Color(0xb2b2b7), 0, h - (i.top + i.bottom), new Color(0x929297)) :
          new GradientPaint(0, i.top, new Color(0x68b2fa), 0, b.getHeight() - (i.top + i.bottom), new Color(0x0e80ff));
    } else {
      Color backgroundColor = (Color)b.getClientProperty("JButton.backgroundColor");
      return backgroundColor != null ? backgroundColor : Gray.xFF;
    }
  }

  @SuppressWarnings("UseJBColor")
  public static Paint getBorderPaint(JComponent b) {
    int h = b.getHeight();
    Insets i = b.getBorder().getBorderInsets(b);

    if (!b.isEnabled()) {
      return new GradientPaint(0, i.top, Gray.xD2, 0, h - (i.top + i.bottom), Gray.xC3);
    } else if (isDefaultButton(b)) {
      return UIUtil.isGraphite() ?
          new GradientPaint(0, i.top, new Color(0xa5a5ab), 0, h - (i.top + i.bottom), new Color(0x7d7d83)) :
          new GradientPaint(0, i.top, new Color(0x4ba0f8), 0, h - (i.top + i.bottom), new Color(0x095eff));
    } else {
      Color borderColor = (Color)b.getClientProperty("JButton.borderColor");
      return borderColor != null ? borderColor : new GradientPaint(0, i.top, Gray.xC9, 0, h - (i.top + i.bottom), Gray.xAC);
    }
  }

  @Override
  protected Dimension getDarculaButtonSize(JComponent c, Dimension prefSize) {
    if (isComboButton(c)) {
      return prefSize;
    } else if (UIUtil.isHelpButton(c)) {
      Icon icon = IconCache.getIcon("helpButton", false, false);
      return new Dimension(icon.getIconWidth(), icon.getIconHeight());
    } else {
      Insets i = c.getInsets();
      return new Dimension(Math.max(JBUI.scale(28) + prefSize.width, JBUI.scale(72) + i.left + i.right),
                           Math.max(prefSize.height, JBUI.scale(21) + i.top + i.bottom));
    }
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

  @Override protected void modifyViewRect(AbstractButton b, Rectangle rect) {
    JBInsets.removeFrom(rect, b.getInsets());
    if (isComboButton(b)) {
      JBInsets.removeFrom(rect, JBUI.insetsLeft(5));
    }
  }
}
