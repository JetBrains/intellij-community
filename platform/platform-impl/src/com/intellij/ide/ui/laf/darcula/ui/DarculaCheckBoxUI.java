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

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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
    if (UIUtil.getParentOfType(CellRendererPane.class, c) != null) {
      c.setBorder(null);
    }
    return new DarculaCheckBoxUI();
  }

  @Override
  public synchronized void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    JCheckBox b = (JCheckBox) c;
    final Dimension size = c.getSize();
    final Font font = c.getFont();

    g.setFont(font);
    FontMetrics fm = SwingUtilities2.getFontMetrics(c, g, font);

    Rectangle viewRect = new Rectangle(size);
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    JBInsets.removeFrom(viewRect, c.getInsets());

    String text = SwingUtilities.layoutCompoundLabel(c, fm, b.getText(), getDefaultIcon(),
                                                     b.getVerticalAlignment(), b.getHorizontalAlignment(),
                                                     b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
                                                     viewRect, iconRect, textRect, b.getIconTextGap());

    //background
    if (c.isOpaque()) {
      g.setColor(b.getBackground());
      g.fillRect(0, 0, size.width, size.height);
    }

    final boolean selected = b.isSelected();
    final boolean enabled = b.isEnabled();
    drawCheckIcon(c, g, b, iconRect, selected, enabled);
    drawText(c, g, b, fm, textRect, text);
  }

  protected void drawCheckIcon(JComponent c, Graphics2D g, JCheckBox b, Rectangle iconRect, boolean selected, boolean enabled) {
    if (selected && b.getSelectedIcon() != null) {
      b.getSelectedIcon().paintIcon(b, g, iconRect.x + JBUI.scale(4), iconRect.y + JBUI.scale(2));
    } else if (!selected && b.getIcon() != null) {
      b.getIcon().paintIcon(b, g, iconRect.x + JBUI.scale(4), iconRect.y + JBUI.scale(2));
    } else {
      int off = JBUI.scale(3);
      final int x = iconRect.x + off;
      final int y = iconRect.y + off;
      final int w = iconRect.width - 2*off;
      final int h = iconRect.height - 2*off;

      g.translate(x, y);
      final Paint paint = UIUtil.getGradientPaint(w / 2, 0, b.getBackground().brighter(),
                                                  w / 2, h, b.getBackground());
      g.setPaint(paint);
      final int fillOffset = JBUI.scale(1);
      g.fillRect(fillOffset, fillOffset, w - 2*fillOffset, h - 2*fillOffset);

      //setup AA for lines
      final GraphicsConfig config = new GraphicsConfig(g);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);

      final boolean armed = b.getModel().isArmed();

      final int R = JBUI.scale(4);
      if (c.hasFocus()) {
        g.setPaint(UIUtil.getGradientPaint(w/2, 1, getFocusedBackgroundColor1(armed, selected), w/2, h, getFocusedBackgroundColor2(armed, selected)));
        g.fillRoundRect(0, 0, w, h, R, R);

        DarculaUIUtil.paintFocusRing(g, 1, 1, w - 2, h - 2);
      } else {
        g.setPaint(UIUtil.getGradientPaint(w / 2, 1, getBackgroundColor1(enabled, selected), w / 2, h, getBackgroundColor2(enabled,
                                                                                                                           selected)));
        g.fillRoundRect(0, 0, w, h , R, R);

        final Color borderColor1 = getBorderColor1(enabled, selected);
        final Color borderColor2 = getBorderColor2(enabled, selected);
        g.setPaint(UIUtil.getGradientPaint(w / 2, 1, borderColor1, w / 2, h, borderColor2));
        g.drawRoundRect(0, (UIUtil.isUnderDarcula() ? 1 : 0), w, h - 1, R, R);

        g.setPaint(getInactiveFillColor());
        g.drawRoundRect(0, 0, w, h - 1, R, R);
      }

      if (b.getModel().isSelected()) {
        paintCheckSign(g, enabled, w, h);
      }
      g.translate(-x, -y);
      config.restore();
    }
  }

  protected void drawText(JComponent c, Graphics2D g, JCheckBox b, FontMetrics fm, Rectangle textRect, String text) {
    //text
    if(text != null) {
      View view = (View) c.getClientProperty(BasicHTML.propertyKey);
      if (view != null) {
        view.paint(g, textRect);
      } else {
        g.setColor(b.isEnabled() ? b.getForeground() : getDisabledTextColor());
        SwingUtilities2.drawStringUnderlineCharAt(c, g, text,
                                                  b.getDisplayedMnemonicIndex(),
                                                  textRect.x,
                                                  textRect.y + fm.getAscent());
      }
    }
  }

  protected void paintCheckSign(Graphics2D g, boolean enabled, int w, int h) {
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.setStroke(new BasicStroke(1 * JBUI.scale(2.0f), BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
    g.setPaint(getShadowColor(enabled, true));
    final int x1 = JBUI.scale(4);
    final int y1 = JBUI.scale(7);
    final int x2 = JBUI.scale(7);
    final int y2 = JBUI.scale(11);
    final int x3 = w;
    final int y3 = JBUI.scale(2);

    g.drawLine(x1, y1, x2, y2);
    g.drawLine(x2, y2, x3, y3);
    g.setPaint(getCheckSignColor(enabled, true));
    g.translate(0, -2);
    g.drawLine(x1, y1, x2, y2);
    g.drawLine(x2, y2, x3, y3);
    g.translate(0, 2);
  }

  protected Color getInactiveFillColor() {
    return getColor("inactiveFillColor", Gray._40.withAlpha(180));
  }

  protected Color getBorderColor1(boolean enabled, boolean selected) {
    return enabled ? getColor("borderColor1", Gray._120.withAlpha(0x5a), selected)
                   : getColor("disabledBorderColor1", Gray._120.withAlpha(90), selected);
  }

  protected Color getBorderColor2(boolean enabled, boolean selected) {
    return enabled ? getColor("borderColor2", Gray._105.withAlpha(90), selected)
                   : getColor("disabledBorderColor2", Gray._105.withAlpha(90), selected);
  }

  protected Color getBackgroundColor1(boolean enabled, boolean selected) {
    return getColor("backgroundColor1", Gray._110, selected);
  }

  protected Color getBackgroundColor2(boolean enabled, boolean selected) {
    return getColor("backgroundColor2", Gray._95, selected);
  }

  protected Color getCheckSignColor(boolean enabled, boolean selected) {
    return enabled ? getColor("checkSignColor", Gray._170, selected)
                   : getColor("checkSignColorDisabled", Gray._120, selected);
  }

  protected Color getShadowColor(boolean enabled, boolean selected) {
    return enabled ? getColor("shadowColor", Gray._30, selected)
                   : getColor("shadowColorDisabled", Gray._60, selected);
  }

  protected Color getFocusedBackgroundColor1(boolean armed, boolean selected) {
    return armed ? getColor("focusedArmed.backgroundColor1", Gray._100, selected)
                 : getColor("focused.backgroundColor1", Gray._120, selected);
  }

  protected Color getFocusedBackgroundColor2(boolean armed, boolean selected) {
    return armed ? getColor("focusedArmed.backgroundColor2", Gray._55, selected)
                 : getColor("focused.backgroundColor2", Gray._75, selected);
  }

  protected static Color getColor(String shortPropertyName, Color defaultValue) {
    final Color color = UIManager.getColor("CheckBox.darcula." + shortPropertyName);
    return color == null ? defaultValue : color;
  }

  protected static Color getColor(String shortPropertyName, Color defaultValue, boolean selected) {
    if (selected) {
      final Color color = getColor(shortPropertyName + ".selected", null);
      if (color != null) {
        return color;
      }
    }
    return getColor(shortPropertyName, defaultValue);
  }

  @Override
  public Icon getDefaultIcon() {
    return JBUI.emptyIcon(20).asUIResource();
  }
}
