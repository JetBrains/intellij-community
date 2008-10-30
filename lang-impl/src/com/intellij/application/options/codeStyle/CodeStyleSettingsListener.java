package com.intellij.application.options.codeStyle;

import java.util.EventListener;

public interface CodeStyleSettingsListener extends EventListener {
  void currentSchemeChanged(final Object source);

  void schemeListChanged();

  void currentSettingsChanged();

  void usePerProjectSettingsOptionChanged();
}
