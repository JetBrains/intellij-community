// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.TextComponentEmptyText;

import javax.swing.text.JTextComponent;

public final class FragmentedSettingsUtil {
  /**
   * @deprecated use {@link TextComponentEmptyText#setupPlaceholderVisibility(JTextComponent)} instead.
   */
  @Deprecated
  public static void setupPlaceholderVisibility(JBTextField textField) {
    TextComponentEmptyText.setupPlaceholderVisibility(textField);
  }
}
