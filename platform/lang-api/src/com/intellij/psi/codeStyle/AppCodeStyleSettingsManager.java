// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@State(name = "CodeStyleSettingsManager", storages = @Storage("code.style.schemes"))
public final class AppCodeStyleSettingsManager extends CodeStyleSettingsManager {
  public AppCodeStyleSettingsManager() {
    registerExtensionPointListeners(null);
  }

  @Override
  protected void registerExtensionPointListeners(@Nullable Disposable disposable) {
    super.registerExtensionPointListeners(disposable);
  }

  @Override
  protected Collection<CodeStyleSettings> enumSettings() {
    List<CodeStyleSettings> appSettings = new ArrayList<>();
    appSettings.add(CodeStyleSettings.getDefaults());
    ObjectUtils.consumeIfNotNull(getMainProjectCodeStyle(), settings -> appSettings.add(settings));
    return appSettings;
  }
}
