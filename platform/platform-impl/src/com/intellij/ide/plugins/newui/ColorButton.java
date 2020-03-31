// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class ColorButton extends JButton {
  @SuppressWarnings("UseJBColor")
  protected static final Color WhiteForeground = new JBColor(Color.white, new Color(0xBBBBBB));

  public ColorButton() {
    setOpaque(false);
  }

  protected final void setTextColor(@NotNull Color color) {
    putClientProperty("JButton.textColor", color);
  }

  protected final void setFocusedTextColor(@NotNull Color color) {
    putClientProperty("JButton.focusedTextColor", color);
  }

  protected final void setBgColor(@NotNull Color color) {
    putClientProperty("JButton.backgroundColor", color);
  }

  protected final void setFocusedBgColor(@NotNull Color color) {
    putClientProperty("JButton.focusedBackgroundColor", color);
  }

  protected final void setBorderColor(@NotNull Color color) {
    putClientProperty("JButton.borderColor", color);
  }

  protected final void setFocusedBorderColor(@NotNull Color color) {
    putClientProperty("JButton.focusedBorderColor", color);
  }

  public static void setWidth72(@NotNull JButton button) {
    setWidth(button, 72);
  }

  public static void setWidth(@NotNull JButton button, int noScaleWidth) {
    int width = JBUIScale.scale(noScaleWidth);
    if (button instanceof JBOptionButton && button.getComponentCount() == 2) {
      width += button.getComponent(1).getPreferredSize().width;
    }
    else {
      Border border = button.getBorder();
      if (border != null) {
        Insets insets = border.getBorderInsets(button);
        width += insets.left + insets.right;
      }
    }
    button.setPreferredSize(new Dimension(width, button.getPreferredSize().height));
  }
}