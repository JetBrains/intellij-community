package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;


@State(
  name = "CodeStyleSettingsManager",
    storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class AppCodeStyleSettingsManager extends CodeStyleSettingsManager{
}
