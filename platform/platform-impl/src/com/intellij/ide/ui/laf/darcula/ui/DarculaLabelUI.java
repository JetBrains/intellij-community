// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
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
    paintText(l, g, s, textX, textY);
  }

  @Override
  protected void paintDisabledText(JLabel l, Graphics g, String s, int textX, int textY) {
    g.setColor(UIManager.getColor("Label.disabledForeground"));
    paintText(l, g, s, textX, textY);
  }

  private static void paintText(JLabel l, Graphics g, String s, int textX, int textY) {
    int mnemIndex = DarculaLaf.isAltPressed() ? l.getDisplayedMnemonicIndex() : -1;
    SwingUtilities2.drawStringUnderlineCharAt(l, g, s, mnemIndex, textX, textY);
  }
}
