// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.TextComponentEmptyText;

import java.util.function.Predicate;

public class FragmentedSettingsUtil {
  public static void setupPlaceholderVisibility(JBTextField textField) {
    textField.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, (Predicate<JBTextField>)f -> StringUtil.isEmpty(f.getText()));
  }
}
