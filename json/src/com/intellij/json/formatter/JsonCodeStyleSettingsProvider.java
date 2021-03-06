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
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings,
                                                           @NotNull CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, JsonBundle.message("settings.display.name.json")) {
      @Override
      protected CodeStyleAbstractPanel createPanel(CodeStyleSettings settings) {
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

  @Nullable
  @Override
  public String getConfigurableDisplayName() {
    return JsonLanguage.INSTANCE.getDisplayName();
  }

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new JsonCodeStyleSettings(settings);
  }
}
