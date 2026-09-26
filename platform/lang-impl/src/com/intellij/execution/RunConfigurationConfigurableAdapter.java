// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorConfigurable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class RunConfigurationConfigurableAdapter<T extends RunConfiguration> extends SettingsEditorConfigurable<T>{
  public RunConfigurationConfigurableAdapter(@NotNull SettingsEditor<T> settingsEditor, @NotNull T configuration) {
    super(settingsEditor, configuration);
 }

  @Override
  public String getDisplayName() {
    return getSettings().getName();
  }

  public T getConfiguration() {
    return getSettings();
  }
}
