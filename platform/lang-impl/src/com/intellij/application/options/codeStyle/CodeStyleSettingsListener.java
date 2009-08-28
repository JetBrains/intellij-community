package com.intellij.application.options.codeStyle;

import com.intellij.psi.codeStyle.CodeStyleScheme;

import java.util.EventListener;

public interface CodeStyleSettingsListener extends EventListener {
  void currentSchemeChanged(final Object source);

  void schemeListChanged();

  void currentSettingsChanged();

  void usePerProjectSettingsOptionChanged();

  void schemeChanged(CodeStyleScheme scheme);
}
