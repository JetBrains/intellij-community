// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class ColorButton extends JButton {
  @SuppressWarnings("UseJBColor")
  protected static final Color WhiteForeground = new JBColor(Color.white, new Color(0xBBBBBB));
  @SuppressWarnings("UseJBColor")
  protected static final Color BlueColor = new JBColor(0x1D73BF, 0x134D80);
  protected static final Color GreenColor = new JBColor(0x5D9B47, 0x2B7B50);
  @SuppressWarnings("UseJBColor")
  protected static final Color GreenFocusedBackground = new Color(0xE1F6DA);

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
}