// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ide.ui.UISettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Helper functions for setting button and label mnemonics based on &amp; characters found
 * in the control text.
 *
 * @author alex
 * @author Konstantin Bulenkov
 */
public final class DialogUtil{

  private DialogUtil() {}

  public static void registerMnemonic(@NotNull AbstractButton button) {
    setTextWithMnemonic(button, button.getText(), UIUtil.MNEMONIC);
  }

  public static void registerMnemonic(@NotNull AbstractButton button, char mn) {
    setTextWithMnemonic(button, button.getText(), mn);
  }

  public static void setTextWithMnemonic(@NotNull AbstractButton button, String text) {
    setTextWithMnemonic(button, text, UIUtil.MNEMONIC);
  }

  public static void setTextWithMnemonic(@NotNull AbstractButton button, String text, char mn) {
    if (text != null) {
      final StringBuilder realText = new StringBuilder();
      char mnemonic = '\0';
      int index = -1;
      for (int i = 0; i < text.length(); i++) {
        final char ch = text.charAt(i);
        if (ch != mn) {
          realText.append(ch);
        } else if (i + 1 < text.length()) {
          mnemonic = text.charAt(i + 1);
          index = realText.length();
        }
      }
      if (mnemonic != '\0') {
        button.setText(realText.toString());
        if (UISettings.getShadowInstance().getDisableMnemonicsInControls()) {
          button.setMnemonic(0);
          button.setDisplayedMnemonicIndex(-1);
          button.setFocusable(true);
        }
        else {
          button.setMnemonic(mnemonic);
          button.setDisplayedMnemonicIndex(index);
        }
      }
    }
  }

  public static void registerMnemonic(JLabel label, @Nullable JComponent target) {
    registerMnemonic(label, target, UIUtil.MNEMONIC);
  }

  /**
   * @param label label
   * @param target target component
   * @param mn mnemonic char
   */
  public static void registerMnemonic(JLabel label, @Nullable JComponent target, char mn) {
    String text = label.getText();
    if (text != null) {
      final StringBuilder realText = new StringBuilder(text.length());
      char mnemonic = '\0';
      int index = -1;
      for (int i = 0; i < text.length(); i++) {
        char ch = text.charAt(i);
        if (ch != mn  || i + 1 == text.length() || text.charAt(i + 1) == ' ') {
          realText.append(ch);
        } else {
          mnemonic = text.charAt(i + 1);
          index = realText.length();
        }
      }
      if (mnemonic != '\0') {
        label.setText(realText.toString());
        if (UISettings.getShadowInstance().getDisableMnemonicsInControls()) {
          label.setDisplayedMnemonic(0);
          label.setDisplayedMnemonicIndex(-1);
        }
        else {
          label.setDisplayedMnemonic(mnemonic);
          label.setDisplayedMnemonicIndex(index);
        }

        if (target != null) {
          label.setLabelFor(target);
        }
      }
    }
  }
}
