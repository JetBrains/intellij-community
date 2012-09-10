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
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ui.MacUIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.metal.MetalCheckBoxUI;
import javax.swing.text.View;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaCheckBoxUI extends MetalCheckBoxUI {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    return new DarculaCheckBoxUI();
  }

  @Override
  public synchronized void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    JCheckBox b = (JCheckBox) c;
    final ButtonModel model = b.getModel();
    final Dimension size = c.getSize();
    final Font font = c.getFont();

    g.setFont(font);
    FontMetrics fm = SwingUtilities2.getFontMetrics(c, g, font);

    Rectangle viewRect = new Rectangle(size);
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    Insets i = c.getInsets();
    viewRect.x += i.left;
    viewRect.y += i.top;
    viewRect.width -= (i.right + viewRect.x);
    viewRect.height -= (i.bottom + viewRect.y);

    String text = SwingUtilities.layoutCompoundLabel(c, fm, b.getText(), getDefaultIcon(),
                                                     b.getVerticalAlignment(), b.getHorizontalAlignment(),
                                                     b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
                                                     viewRect, iconRect, textRect, b.getIconTextGap());

    //background
    if(c.isOpaque()) {
      g.setColor(b.getBackground());
      g.fillRect(0,0, size.width, size.height);
    }

    final int x = iconRect.x + 2;
    final int y = iconRect.y + 2;
    final int w = iconRect.width - 4;
    final int h = iconRect.height - 4;
    final int u = w / 16;

    g.translate(x, y);
    final GradientPaint paint = new GradientPaint(w / 2, 0, b.getBackground().brighter(),
                                                  w / 2, h, b.getBackground());
    g.setPaint(paint);
    g.fillRect(u, u, w - 2 * u, h - 2 * u);

    //setup AA for lines
    final GraphicsConfig config = new GraphicsConfig(g);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);

    final boolean armed = b.getModel().isArmed();

    if (c.hasFocus()) {
      g.setPaint(new GradientPaint(w/2, 1, armed ? Gray._40: Gray._60, w/2, h, armed ? Gray._25 : Gray._45));
      g.fillRoundRect(0, 0, w - 2, h - 2, 4, 4);

      MacUIUtil.paintFocusRing(g, new Color(96, 175, 255), new Rectangle(1, 1, w - 2, h - 2));
    } else {
      g.setPaint(new GradientPaint(w / 2, 1, Gray._80, w / 2, h, Gray._65));
      g.fillRoundRect(0, 0, w - 2, h - 2, 4, 4);

      g.setPaint(new GradientPaint(w / 2, 1, ColorUtil.toAlpha(Gray._120, 90), w / 2, h, ColorUtil.toAlpha(Gray._105, 90)));
      g.drawRoundRect(0, 1, w, h - 1, 4, 4);

      g.setPaint(Gray._30);
      g.drawRoundRect(0, 0, w, h - 1, 4, 4);
    }

    if (b.getModel().isSelected()) {
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setStroke(new BasicStroke(u*2.0f, BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
      g.setPaint(Gray._30);
      g.drawLine(4*u, 8*u, 8*u, 12*u);
      g.drawLine(8*u, 12*u, w, 2*u);
      g.setPaint(Gray._200);
      g.drawLine(4*u, 6*u, 8*u, 10*u);
      g.drawLine(8*u, 10*u, w, 0);
    }
    g.translate(-x, -y);
    config.restore();

    //text
    if(text != null) {
      View view = (View) c.getClientProperty(BasicHTML.propertyKey);
      if (view != null) {
        view.paint(g, textRect);
      } else {
        g.setColor(model.isEnabled() ? b.getForeground() : getDisabledTextColor());
        SwingUtilities2.drawStringUnderlineCharAt(c, g, text,
                                                  b.getDisplayedMnemonicIndex(),
                                                  textRect.x,
                                                  textRect.y + fm.getAscent());
      }
    }
  }
}
