// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.DisplayPriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contains settings for non-language options, for example, text files.
 */
public final class OtherFileTypesCodeStyleOptionsProvider extends CodeStyleSettingsProvider {

  @Override
  public @NotNull Configurable createSettingsPage(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings clonedSettings) {
    return new OtherFileTypesCodeStyleConfigurable(settings, clonedSettings);
  }

  @Override
  public @Nullable String getConfigurableDisplayName() {
    return ApplicationBundle.message("code.style.other.file.types");
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.OTHER_SETTINGS;
  }
}
