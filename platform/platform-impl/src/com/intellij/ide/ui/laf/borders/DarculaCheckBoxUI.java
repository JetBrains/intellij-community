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
  public static ComponentUI createUI(JComponent c) {
    return new DarculaCheckBoxUI();
  }

  @Override
  public synchronized void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    JCheckBox b = (JCheckBox) c;
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

    Icon altIcon = b.getIcon();

    String text = SwingUtilities.layoutCompoundLabel(
      c, fm, b.getText(), altIcon != null ? altIcon : getDefaultIcon(),
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect, b.getIconTextGap());

    // fill background
    if(c.isOpaque()) {
      g.setColor(b.getBackground());
      g.fillRect(0,0, size.width, size.height);
    }

    //paint icon bg
    int width = iconRect.width;
    int height = iconRect.height;
    final GradientPaint paint = new GradientPaint(iconRect.x + width / 2, iconRect.y, b.getBackground().brighter(),
                                                  iconRect.x + width / 2, iconRect.y + height, b.getBackground());
    g.setPaint(paint);
    g2d.fillRect(iconRect.x + 1, iconRect.y + 1, width - 2, height - 2);




    final GraphicsConfig config = new GraphicsConfig(g);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);

    final boolean armed = b.getModel().isArmed();

    if (c.hasFocus()) {
      g.setPaint(new GradientPaint(width / 2, 1, armed ? Gray._40: Gray._60, width / 2, height, armed ? Gray._25 : Gray._45));
      g.fillRoundRect(iconRect.x, iconRect.y, width-2, height-2, 4, 4);

      MacUIUtil.paintFocusRing(g, new Color(96, 175, 255), new Rectangle(iconRect.x+1, iconRect.y + 1, iconRect.width-2,iconRect.height-2));
    } else {
      g.setPaint(new GradientPaint(width / 2, 1, Gray._80, width / 2, height, Gray._65));
      g.fillRoundRect(iconRect.x, iconRect.y, width-2, height-2, 4, 4);

      g.setPaint(new GradientPaint(width / 2, 1, ColorUtil.toAlpha(Gray._120, 90), width / 2, height, ColorUtil.toAlpha(Gray._105, 90)));
      g.drawRoundRect(iconRect.x, iconRect.y + 1, iconRect.width, iconRect.height - 1, 4, 4);


      g.setPaint(Gray._30);
      g.drawRoundRect(iconRect.x, iconRect.y, width, height-1, 4, 4);

    }

    if (b.getModel().isSelected()) {
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
      g.setPaint(Gray._30);
      g.drawLine(iconRect.x + 4, iconRect.y + 8, iconRect.x + 9, iconRect.y + 14);
      g.drawLine(iconRect.x + 9, iconRect.y + 14, iconRect.x + iconRect.width, iconRect.y+2);
      g.setPaint(Gray._200);
      g.drawLine(iconRect.x + 4, iconRect.y + 6, iconRect.x + 9, iconRect.y + 12);
      g.drawLine(iconRect.x + 9, iconRect.y + 12, iconRect.x + iconRect.width, iconRect.y);
    }
    config.restore();
    //icon.paintIcon(c, g, iconRect.x, iconRect.y);

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
        SwingUtilities2.drawStringUnderlineCharAt(c,g,text,
                                                  mnemIndex, textRect.x, textRect.y + fm.getAscent());
      }
    }
  }

  public Icon getSelectedIcon() {
    return UIManager.getIcon(getPropertyPrefix() + "selectedIcon");
  }
}
