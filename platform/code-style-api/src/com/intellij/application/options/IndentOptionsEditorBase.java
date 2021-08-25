// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

import javax.swing.*;

public interface IndentOptionsEditorBase extends CodeStyleSettingsCustomizable {
  JPanel createPanel();

  void setEnabled(boolean enabled);

  boolean isModified(CodeStyleSettings settings, CommonCodeStyleSettings.IndentOptions options);

  void reset(CodeStyleSettings settings, CommonCodeStyleSettings.IndentOptions options);

  void apply(CodeStyleSettings settings, CommonCodeStyleSettings.IndentOptions options);
}
