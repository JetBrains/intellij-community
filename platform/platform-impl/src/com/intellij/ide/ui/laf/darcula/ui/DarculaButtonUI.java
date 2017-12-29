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
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

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
    return button instanceof JButton && "help".equals(button.getClientProperty("JButton.buttonType"));
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
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        if (c.isEnabled()) {
          if (isSquare(c)) {

            Rectangle r = new Rectangle(w, h);
            //JBInsets.removeFrom(r, JBUI.insets(1));
            g2.translate(r.x, r.y);
            g2.setPaint(UIUtil.getGradientPaint(r.x, r.y, getButtonColor1(), r.x + r.width,
                                                r.y + r.height, getButtonColor2()));
            float arc = JBUI.scale(2.0f);
            float bw = DarculaUIUtil.bw();
            g2.fill(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc));
          }
          else {
            g2.setPaint(isDefaultButton(c) ?
                        UIUtil.getGradientPaint(0, 0, getSelectedButtonColor1(), 0, h, getSelectedButtonColor2()) :
                        UIUtil.getGradientPaint(0, 0, getButtonColor1(), 0, h, getButtonColor2()));

            Insets ins = c.getInsets();
            int yOff = (ins.top + ins.bottom) / 4;
            int rad = JBUI.scale(5);
            g2.fillRoundRect(JBUI.scale(4), yOff, w - 2 * JBUI.scale(4), h - 2 * yOff, rad, rad);
          }
        }
      } finally {
        g2.dispose();
      }
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
