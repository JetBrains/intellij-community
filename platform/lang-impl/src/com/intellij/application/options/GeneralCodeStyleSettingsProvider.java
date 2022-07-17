// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.DisplayPriority;
import org.jetbrains.annotations.NotNull;


public class GeneralCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  @NotNull
  public Configurable createSettingsPage(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings originalSettings) {
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
