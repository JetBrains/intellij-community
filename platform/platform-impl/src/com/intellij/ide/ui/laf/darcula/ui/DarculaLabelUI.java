// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicLabelUI;
import java.awt.*;

public class DarculaLabelUI extends BasicLabelUI {
  private static boolean isMnemonicHidden = true;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaLabelUI();
  }

  @Override
  protected void paintEnabledText(final JLabel l, final Graphics g, final String s, final int textX, final int textY) {
    int mnemIndex = l.getDisplayedMnemonicIndex();
    if (isMnemonicHidden()) {
      mnemIndex = -1;
    }

    g.setColor(l.getForeground());
    SwingUtilities2.drawStringUnderlineCharAt(l, g, s, mnemIndex, textX, textY);
  }

  @Override
  protected void paintDisabledText(final JLabel l, final Graphics g, final String s, final int textX, final int textY) {
    int mnemIndex = l.getDisplayedMnemonicIndex();
    if (isMnemonicHidden()) {
      mnemIndex = -1;
    }

    g.setColor(UIManager.getColor("Label.disabledForeground"));
    SwingUtilities2.drawStringUnderlineCharAt(l, g, s, mnemIndex, textX, textY);
  }

  private static boolean isMnemonicHidden() {
    if (UIManager.getBoolean("Button.showMnemonics")) {
      isMnemonicHidden = false;
    }
    return isMnemonicHidden;
  }
}
