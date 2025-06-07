// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.formatter;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
final class JsonCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings,
                                                           @NotNull CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, JsonBundle.message("settings.display.name.json")) {
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(@NotNull CodeStyleSettings settings) {
        final Language language = JsonLanguage.INSTANCE;
        final CodeStyleSettings currentSettings = getCurrentSettings();
        return new TabbedLanguageCodeStylePanel(language, currentSettings, settings) {
          @Override
          protected void initTabs(CodeStyleSettings settings) {
            addIndentOptionsTab(settings);
            addSpacesTab(settings);
            addBlankLinesTab(settings);
            addWrappingAndBracesTab(settings);
          }
        };
      }

      @Override
      public @NotNull String getHelpTopic() {
        return "reference.settingsdialog.codestyle.json";
      }
    };
  }

  @Override
  public @NotNull String getConfigurableDisplayName() {
    return JsonLanguage.INSTANCE.getDisplayName();
  }

  @Override
  public @NotNull CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
    return new JsonCodeStyleSettings(settings);
  }

  @Override
  public @NotNull Language getLanguage() {
    return JsonLanguage.INSTANCE;
  }
}
