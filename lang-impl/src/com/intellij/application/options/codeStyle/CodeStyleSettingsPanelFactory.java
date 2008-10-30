package com.intellij.application.options.codeStyle;

import com.intellij.psi.codeStyle.CodeStyleScheme;

public interface CodeStyleSettingsPanelFactory {
  NewCodeStyleSettingsPanel createPanel(final CodeStyleScheme scheme);
}
