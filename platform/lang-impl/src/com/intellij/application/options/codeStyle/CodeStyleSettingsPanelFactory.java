package com.intellij.application.options.codeStyle;

import com.intellij.psi.codeStyle.CodeStyleScheme;

public abstract class CodeStyleSettingsPanelFactory {
  public abstract NewCodeStyleSettingsPanel createPanel(final CodeStyleScheme scheme);
}
