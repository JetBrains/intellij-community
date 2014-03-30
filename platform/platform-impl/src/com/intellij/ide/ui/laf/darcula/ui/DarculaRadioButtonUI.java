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
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
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

    int rad = 5;

    // Paint the radio button
    final int x = iconRect.x + (rad-1)/2;
    final int y = iconRect.y + (rad-1)/2;
    final int w = iconRect.width - (rad + 5) / 2;
    final int h = iconRect.height - (rad + 5) / 2;

    g.translate(x, y);

    //setup AA for lines
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    final boolean focus = b.hasFocus();
    g.setPaint(UIUtil.getGradientPaint(0, 0, ColorUtil.shift(c.getBackground(), 1.5),
                                       0, c.getHeight(), ColorUtil.shift(c.getBackground(), 1.2)));
    if (focus) {
      g.fillOval(0, 1, w, h);
    } else {
      g.fillOval(0, 1, w - 1, h - 1);
    }

    if (focus) {
      if (UIUtil.isRetina()) {
        DarculaUIUtil.paintFocusOval(g, 1, 2, w-2, h-2);
      } else {
        DarculaUIUtil.paintFocusOval(g, 0, 1, w, h);
      }
    } else {
      if (UIUtil.isUnderDarcula()) {
        g.setPaint(UIUtil.getGradientPaint(w / 2, 1, Gray._160.withAlpha(90), w / 2, h, Gray._100.withAlpha(90)));
        g.drawOval(0, 2, w - 1, h - 1);

        g.setPaint(Gray._40.withAlpha(200));
        g.drawOval(0, 1, w - 1, h - 1);
      } else {
        g.setPaint(b.isEnabled() ? Gray._30 : Gray._130);
        g.drawOval(0, 1, w - 1, h - 1);
      }
    }

    if (b.isSelected()) {
      final boolean enabled = b.isEnabled();
      g.setColor(UIManager.getColor(enabled ? "RadioButton.darcula.selectionEnabledShadowColor" : "RadioButton.darcula.selectionDisabledShadowColor"));// ? Gray._30 : Gray._60);
      g.fillOval(w/2 - rad/2, h/2 , rad, rad);
      g.setColor(UIManager.getColor(enabled ? "RadioButton.darcula.selectionEnabledColor" : "RadioButton.darcula.selectionDisabledColor")); //Gray._170 : Gray._120);
      g.fillOval(w/2 - rad/2, h/2 - 1, rad, rad);
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
