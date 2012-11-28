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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.util.ui.EmptyIcon;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.IconUIResource;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.metal.MetalRadioButtonUI;
import javax.swing.text.View;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRadioButtonUI extends MetalRadioButtonUI {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    return new DarculaRadioButtonUI();
  }

  @Override
  public synchronized void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    AbstractButton b = (AbstractButton) c;
    ButtonModel model = b.getModel();

    Dimension size = c.getSize();
    Font f = c.getFont();
    g.setFont(f);
    FontMetrics fm = SwingUtilities2.getFontMetrics(c, g, f);

    Rectangle viewRect = new Rectangle(size);
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    Insets i = c.getInsets();
    viewRect.x += i.left;
    viewRect.y += i.top;
    viewRect.width -= (i.right + viewRect.x);
    viewRect.height -= (i.bottom + viewRect.y);


    String text = SwingUtilities.layoutCompoundLabel(
      c, fm, b.getText(), getDefaultIcon(),
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect, b.getIconTextGap());

    // fill background
    if(c.isOpaque()) {
      g.setColor(b.getBackground());
      g.fillRect(0,0, size.width, size.height);
    }


    // Paint the radio button
    final int x = iconRect.x + 2;
    final int y = iconRect.y + 2;
    final int w = iconRect.width - 4;
    final int h = iconRect.height - 4;
    final int u = w / 16;

    g.translate(x, y);

    //setup AA for lines
    final GraphicsConfig config = new GraphicsConfig(g);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

    if (b.hasFocus()) {
      int sysOffX = SystemInfo.isMac ? 0 : 1;
      int sysOffY = SystemInfo.isMac ? 0 : -1;
      DarculaUIUtil.paintFocusOval(g, x-6  + sysOffX, y-3 + sysOffY, w-3, h-3);
    } else {
      g.setPaint(new GradientPaint(w / 2, 1, Gray._180.withAlpha(90), w / 2, h, Gray._125.withAlpha(90)));
      g.drawOval(0, 2, w - 2, h - 2);

      g.setPaint(Gray._40.withAlpha(200));
      g.drawOval(0, 1, w - 2, h - 2);
    }

    if (b.isSelected()) {
      g.setColor(Gray._0.withAlpha(50));
      g.fillOval(w/2 - 3*u+1, h/2 - 3*u + 2, 5*u, 5*u);
      g.setColor(Gray._255.withAlpha(180));
      g.fillOval(w/2 - 3*u, h/2 - 3*u + 1, 5*u, 5*u);
    }
    config.restore();
    g.translate(-x, -y);

    // Draw the Text
    if(text != null) {
      View v = (View) c.getClientProperty(BasicHTML.propertyKey);
      if (v != null) {
        v.paint(g, textRect);
      } else {
        int mnemIndex = b.getDisplayedMnemonicIndex();
        if(model.isEnabled()) {
          // *** paint the text normally
          g.setColor(b.getForeground());
        } else {
          // *** paint the text disabled
          g.setColor(getDisabledTextColor());
        }
        SwingUtilities2.drawStringUnderlineCharAt(c, g, text,
                                                  mnemIndex, textRect.x, textRect.y + fm.getAscent());
      }
    }
  }

  @Override
  public Icon getDefaultIcon() {
    return new IconUIResource(EmptyIcon.create(20));
  }
}
