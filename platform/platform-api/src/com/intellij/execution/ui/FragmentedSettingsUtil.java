// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.BooleanFunction;

public class FragmentedSettingsUtil {

  public static void setupPlaceholderVisibility(JBTextField textField) {
    textField.putClientProperty("StatusVisibleFunction",
                                (BooleanFunction<JBTextField>)field -> !StringUtil.isNotEmpty(field.getText()));

  }
}
