// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.components.labels.DropDownLink;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JLabelUtil;
import com.intellij.util.ui.SwingTextTrimmer;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.basic.BasicLabelUI;
import java.awt.*;

public class DarculaLabelUI extends BasicLabelUI {
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaLabelUI();
  }

  @Override
  protected void paintEnabledText(JLabel l, Graphics g, String s, int textX, int textY) {
    g.setColor(l.getForeground());
    SwingUtilities2.drawStringUnderlineCharAt(l, g, s, getMnemonicIndex(l), textX, textY);
  }

  @Override
  protected void paintDisabledText(JLabel l, Graphics g, String s, int textX, int textY) {
    g.setColor(UIManager.getColor("Label.disabledForeground"));
    SwingUtilities2.drawStringUnderlineCharAt(l, g, s, -1, textX, textY);
  }

  protected int getMnemonicIndex(JLabel l) {
    return !SystemInfo.isMac ? l.getDisplayedMnemonicIndex() : -1;
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    boolean canBeTrimmed = c.getClientProperty(BasicHTML.propertyKey) == null
                           && ClientProperty.get(c, JLabelUtil.TRIM_OVERFLOW_KEY) != null;
    if (!canBeTrimmed) {
      return super.getMinimumSize(c);
    }


    JLabel label = (JLabel)c;
    String text = label.getText();
    Icon icon = (label.isEnabled()) ? label.getIcon() : label.getDisabledIcon();
    Insets insets = label.getInsets(null);
    Font font = label.getFont();

    int xInsets = insets.left + insets.right;
    int yInsets = insets.top + insets.bottom;

    if (icon == null && (text == null || font == null)) {
      return new Dimension(xInsets, yInsets);
    }

    if (icon != null && (text == null || font == null)) {
      return new Dimension(icon.getIconWidth() + xInsets, icon.getIconHeight() + yInsets);
    }
    else {
      Rectangle allocation = new Rectangle();

      FontMetrics fm = label.getFontMetrics(font);
      Rectangle iconR = new Rectangle();
      Rectangle textR = new Rectangle();

      allocation.x = insets.left;
      allocation.y = insets.top;
      allocation.width = insets.right;
      allocation.height = insets.bottom;

      layoutCL(label, fm, text, icon, allocation, iconR, textR);
      int x1 = Math.min(iconR.x, textR.x);
      int x2 = Math.max(iconR.x + iconR.width, textR.x + textR.width);
      int y1 = Math.min(iconR.y, textR.y);
      int y2 = Math.max(iconR.y + iconR.height, textR.y + textR.height);
      Dimension rv = new Dimension(x2 - x1, y2 - y1);

      rv.width += xInsets;
      rv.height += yInsets;
      return rv;
    }
  }

  @Override
  protected String layoutCL(JLabel label, FontMetrics fontMetrics, String text, Icon icon,
                            Rectangle viewR, Rectangle iconR, Rectangle textR) {
    String result = super.layoutCL(label, fontMetrics, text, icon, viewR, iconR, textR);
    if (!StringUtil.isEmpty(result)) {
      SwingTextTrimmer trimmer = ClientProperty.get(label, SwingTextTrimmer.KEY);
      if (trimmer != null && null == label.getClientProperty(BasicHTML.propertyKey)) {
        if (!result.equals(text) && result.endsWith(StringUtil.THREE_DOTS)) {
          result = trimmer.trim(text, fontMetrics, textR.width);
        }
      }
    }

    if (label instanceof DropDownLink) {
      iconR.y += JBUIScale.scale(1);
    }
    return result;
  }
}
