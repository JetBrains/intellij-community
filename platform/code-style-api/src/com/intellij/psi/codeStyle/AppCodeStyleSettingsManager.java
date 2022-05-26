// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@State(name = "CodeStyleSettingsManager", storages = @Storage("code.style.schemes"), category = SettingsCategory.CODE)
public final class AppCodeStyleSettingsManager extends CodeStyleSettingsManager {
  public AppCodeStyleSettingsManager() {
    registerExtensionPointListeners(null);
  }

  @Override
  protected @NotNull Collection<CodeStyleSettings> enumSettings() {
    List<CodeStyleSettings> appSettings = new ArrayList<>();
    appSettings.add(CodeStyleSettings.getDefaults());
    CodeStyleSettings settings = getMainProjectCodeStyle();
    if (settings != null) {
      appSettings.add(settings);
    }
    return appSettings;
  }
}
