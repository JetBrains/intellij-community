package com.intellij.json.formatter;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.SPACES_OTHER;

/**
 * @author Mikhail Golubev
 */
public class JsonLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  private static final String SAMPLE = "{\n" +
                                       "    \"json literals are\": {\n" +
                                       "        \"strings\": [\"foo\", \"bar\", \"\\u0062\\u0061\\u0072\"],\n" +
                                       "        \"numbers\": [42, 6.62606975e-34],\n" +
                                       "        \"boolean values\": [true, false],\n" +
                                       "        \"and\": {\"null\": null}\n" +
                                       "    }\n" +
                                       "}";


  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.showStandardOptions("SPACE_WITHIN_BRACKETS",
                                   "SPACE_WITHIN_BRACES",
                                   "SPACE_AFTER_COMMA",
                                   "SPACE_BEFORE_COMMA");
      consumer.renameStandardOption("SPACE_WITHIN_BRACES", "Braces");
      consumer.showCustomOption(JsonCodeStyleSettings.class, "SPACE_BEFORE_COLON", "Before ':'", SPACES_OTHER);
      consumer.showCustomOption(JsonCodeStyleSettings.class, "SPACE_AFTER_COLON", "After ':'", SPACES_OTHER);
    }
    else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      consumer.showStandardOptions("KEEP_BLANK_LINES_IN_CODE");
    }
    else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions("RIGHT_MARGIN",
                                   "WRAP_ON_TYPING",
                                   "KEEP_LINE_BREAKS",
                                   "WRAP_LONG_LINES");

      consumer.showCustomOption(JsonCodeStyleSettings.class,
                                "ARRAY_WRAPPING",
                                "Arrays",
                                null,
                                CodeStyleSettingsCustomizable.WRAP_OPTIONS,
                                CodeStyleSettingsCustomizable.WRAP_VALUES);

      consumer.showCustomOption(JsonCodeStyleSettings.class,
                                "OBJECT_WRAPPING",
                                "Objects",
                                null,
                                CodeStyleSettingsCustomizable.WRAP_OPTIONS,
                                CodeStyleSettingsCustomizable.WRAP_VALUES);

    }
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return JsonLanguage.INSTANCE;
  }

  @Nullable
  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return SAMPLE;
  }

  @Nullable
  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings commonSettings = new CommonCodeStyleSettings(JsonLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.initIndentOptions();
    indentOptions.INDENT_SIZE = 2;
    // strip all blank lines by default
    commonSettings.KEEP_BLANK_LINES_IN_CODE = 0;
    return commonSettings;
  }
}
