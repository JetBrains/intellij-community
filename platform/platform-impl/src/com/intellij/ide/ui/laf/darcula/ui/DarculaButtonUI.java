/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBInsets;
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
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaButtonUI extends BasicButtonUI {
  private Rectangle viewRect = new Rectangle();
  private Rectangle textRect = new Rectangle();
  private Rectangle iconRect = new Rectangle();

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaButtonUI();
  }

  public static boolean isSquare(Component c) {
    return c instanceof JButton && "square".equals(((JButton)c).getClientProperty("JButton.buttonType"));
  }

  public static boolean isDefaultButton(JComponent c) {
    return c instanceof JButton && ((JButton)c).isDefaultButton();
  }

  public static boolean isComboButton(JComponent c) {
    return c instanceof AbstractButton && c.getClientProperty("styleCombo") == Boolean.TRUE;
  }

  public static boolean isHelpButton(JComponent button) {
    return (SystemInfo.isMac || UIUtil.isUnderDarcula() || UIUtil.isUnderWin10LookAndFeel())
           && button instanceof JButton
           && "help".equals(button.getClientProperty("JButton.buttonType"));
  }

  /**
   * Paints additional buttons decorations
   * @param g Graphics
   * @param c button component
   * @return {@code true} if it is allowed to continue painting,
   *         {@code false} if painting should be stopped
   */
  protected boolean paintDecorations(Graphics2D g, JComponent c) {
    int w = c.getWidth();
    int h = c.getHeight();
    if (isHelpButton(c)) {
      g.setPaint(UIUtil.getGradientPaint(0, 0, getButtonColor1(), 0, h, getButtonColor2()));
      int off = JBUI.scale(22);
      int x = (w - off) / 2;
      int y = (h - off) / 2;
      g.fillOval(x, y, off, off);
      AllIcons.Actions.Help.paintIcon(c, g, x + JBUI.scale(3), y + JBUI.scale(3));
      return false;
    } else {
      final Border border = c.getBorder();
      final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      final boolean square = isSquare(c);
      if (c.isEnabled() && border != null) {
        final Insets ins = border.getBorderInsets(c);
        final int yOff = (ins.top + ins.bottom) / 4;
        if (!square) {
          if (isDefaultButton(c)) {
            g.setPaint(UIUtil.getGradientPaint(0, 0, getSelectedButtonColor1(), 0, h, getSelectedButtonColor2()));
          }
          else {
            g.setPaint(UIUtil.getGradientPaint(0, 0, getButtonColor1(), 0, h, getButtonColor2()));
          }
        }
        int rad = JBUI.scale(square ? 3 : 5);
        g.fillRoundRect(JBUI.scale(square ? 2 : 4), yOff, w - 2 * JBUI.scale(4), h - 2 * yOff, rad, rad);
      }
      config.restore();
      return true;
    }
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    if (paintDecorations((Graphics2D)g, c)) {
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

    //UISettings.setupAntialiasing(g);

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

  protected void paintContents(Graphics g, AbstractButton b) {
    FontMetrics fm = SwingUtilities2.getFontMetrics(b, g);
    boolean isDotButton = isSquare(b) && b.getIcon() == AllIcons.General.Ellipsis;
    String text = isDotButton ? "..." : b.getText();
    Icon icon = isDotButton ? null : b.getIcon();
    text = layout(b, text, icon, fm, b.getWidth(), b.getHeight());

    if (isSquare(b)) {
      if (b.getIcon() == AllIcons.General.Ellipsis) {
        UISettings.setupAntialiasing(g);
        paintText(g, b, textRect, text);
      } else if (b.getIcon() != null) {
        paintIcon(g, b, iconRect);
      }
    } else {
      // Paint the Icon
      if (b.getIcon() != null) {
        paintIcon(g, b, iconRect);
      }

      if (text != null && !text.isEmpty()){
        View v = (View) b.getClientProperty(BasicHTML.propertyKey);
        if (v != null) {
          v.paint(g, textRect);
        } else {
          UISettings.setupAntialiasing(g);
          paintText(g, b, textRect, text);
        }
      }
    }
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
    if (isDefaultButton(c)) {
      setupDefaultButton((JButton)c);
    }
  }

  protected void setupDefaultButton(JButton button) {
    if (!SystemInfo.isMac) {
      if (!button.getFont().isBold()) {
       button.setFont(new FontUIResource(button.getFont().deriveFont(Font.BOLD)));
      }
    }
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

  protected String layout(AbstractButton b, String text, Icon icon, FontMetrics fm, int width, int height) {
    textRect.setBounds(0, 0, 0, 0);
    iconRect.setBounds(0, 0, 0, 0);

    viewRect.setBounds(0, 0, width, height);
    modifyViewRect(b, viewRect);

    // layout the text and icon
    return SwingUtilities.layoutCompoundLabel(
      b, fm, text, icon,
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect, text == null ? 0 : b.getIconTextGap());
  }

  protected void modifyViewRect(AbstractButton b, Rectangle rect) {
    JBInsets.removeFrom(rect, b.getInsets());

    if (isComboButton(b)) {
      rect.x += 6;
    } else if (b instanceof JBOptionButton) {
      rect.x -= 4;
    }
  }
}
