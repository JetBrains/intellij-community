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

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaButtonUI extends BasicButtonUI {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    return new DarculaButtonUI();
  }

  public static boolean isSquare(Component c) {
    return c instanceof JButton && "square".equals(((JButton)c).getClientProperty("JButton.buttonType"));
  }

  public static boolean isDefaultButton(JComponent c) {
    return c instanceof JButton && ((JButton)c).isDefaultButton();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    int w = c.getWidth();
    int h = c.getHeight();
    if (isHelpButton(c)) {
      ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, getButtonColor1(), 0, h, getButtonColor2()));
      int off = JBUI.scale(22);
      int x = (w - off) / 2;
      int y = (h - off) / 2;
      g.fillOval(x, y, off, off);
      AllIcons.Actions.Help.paintIcon(c, g, x + JBUI.scale(3), y + JBUI.scale(3));
    } else {
      final Border border = c.getBorder();
      final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      final boolean square = isSquare(c);
      if (c.isEnabled() && border != null) {
        final Insets ins = border.getBorderInsets(c);
        final int yOff = (ins.top + ins.bottom) / 4;
        if (!square) {
          if (isDefaultButton(c)) {
            ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, getSelectedButtonColor1(), 0, h, getSelectedButtonColor2()));
          }
          else {
            ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, getButtonColor1(), 0, h, getButtonColor2()));
          }
        }
        int rad = JBUI.scale(square ? 3 : 5);
        g.fillRoundRect(JBUI.scale(square ? 2 : 4), yOff, w - 2 * JBUI.scale(4), h - 2 * yOff, rad, rad);
      }
      config.restore();
      super.paint(g, c);
    }
  }

  protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
    if (isHelpButton(c)) {
      return;
    }
    
    AbstractButton button = (AbstractButton)c;
    ButtonModel model = button.getModel();
    Color fg = button.getForeground();
    if (fg instanceof UIResource && isDefaultButton(button)) {
      final Color selectedFg = UIManager.getColor("Button.darcula.selectedButtonForeground");
      if (selectedFg != null) {
        fg = selectedFg;
      }
    }
    g.setColor(fg);

    FontMetrics metrics = SwingUtilities2.getFontMetrics(c, g);
    int mnemonicIndex = DarculaLaf.isAltPressed() ? button.getDisplayedMnemonicIndex() : -1;
    if (model.isEnabled()) {

      SwingUtilities2.drawStringUnderlineCharAt(c, g, text, mnemonicIndex,
                                                textRect.x + getTextShiftOffset(),
                                                textRect.y + metrics.getAscent() + getTextShiftOffset());
    }
    else {
      paintDisabledText(g, text, c, textRect, metrics);
    }
  }

  protected void paintDisabledText(Graphics g, String text, JComponent c, Rectangle textRect, FontMetrics metrics) {
    g.setColor(UIManager.getColor("Button.darcula.disabledText.shadow"));
    SwingUtilities2.drawStringUnderlineCharAt(c, g, text, -1,
                                              textRect.x + getTextShiftOffset()+1,
                                              textRect.y + metrics.getAscent() + getTextShiftOffset()+1);
    g.setColor(UIManager.getColor("Button.disabledText"));
    SwingUtilities2.drawStringUnderlineCharAt(c, g, text, -1,
                                              textRect.x + getTextShiftOffset(),
                                              textRect.y + metrics.getAscent() + getTextShiftOffset());
  }

  @Override
  protected void paintIcon(Graphics g, JComponent c, Rectangle iconRect) {
    Border border = c.getBorder();
    if (border != null && isSquare(c)) {
      int xOff = 1;
      Insets ins = border.getBorderInsets(c);
      int yOff = (ins.top + ins.bottom) / 4;
      Rectangle iconRect2 = new Rectangle(iconRect);
      iconRect2.x += xOff;
      iconRect2.y += yOff;
      super.paintIcon(g, c, iconRect2);
    }
    else {
      super.paintIcon(g, c, iconRect);
    }
  }

  @Override
  public void update(Graphics g, JComponent c) {
    super.update(g, c);
    if (isDefaultButton(c) && !SystemInfo.isMac) {
      if (!c.getFont().isBold()) {
       c.setFont(new FontUIResource(c.getFont().deriveFont(Font.BOLD)));
      }
    }
  }
  
  public static boolean isHelpButton(JComponent button) {
    return SystemInfo.isMac 
           && button instanceof JButton 
           && "help".equals(button.getClientProperty("JButton.buttonType"));
  }

  protected Color getButtonColor1() {
    return ObjectUtils.notNull(UIManager.getColor("Button.darcula.color1"), new ColorUIResource(0x555a5c));
  }

  protected Color getButtonColor2() {
    return ObjectUtils.notNull(UIManager.getColor("Button.darcula.color2"), new ColorUIResource(0x414648));
  }

  protected Color getSelectedButtonColor1() {
    return ObjectUtils.notNull(UIManager.getColor("Button.darcula.selection.color1"), new ColorUIResource(0x384f6b));
  }

  protected Color getSelectedButtonColor2() {
    return ObjectUtils.notNull(UIManager.getColor("Button.darcula.selection.color2"), new ColorUIResource(0x233143));
  }
}
