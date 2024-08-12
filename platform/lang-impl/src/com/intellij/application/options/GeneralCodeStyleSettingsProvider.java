// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.DisplayPriority;
import org.jetbrains.annotations.NotNull;


public final class GeneralCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  public @NotNull Configurable createSettingsPage(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, ApplicationBundle.message("title.general")) {
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(final @NotNull CodeStyleSettings settings) {
        return new GeneralCodeStylePanel(settings);
      }

      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.IDE.globalcodestyle.general";
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return ApplicationBundle.message("title.general");
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.GENERAL_SETTINGS;
  }
}
