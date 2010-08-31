/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Base class and extension point for code style settings shared between multiple languages
 */
public abstract class LanguageCodeStyleSettingsProvider {
  public static final ExtensionPointName<LanguageCodeStyleSettingsProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.langCodeStyleSettingsProvider");

  public enum SettingsType {
    BLANK_LINES_SETTINGS, SPACING_SETTINGS, WRAPPING_AND_BRACES_SETTINGS, LANGUAGE_SPECIFIC
  }

  @NotNull
  public abstract Language getLanguage();

  public abstract String getCodeSample(@NotNull SettingsType settingsType);

  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
  }

  /**
   * Creates an instance of <code>CommonCodeStyleSettings</code> and sets initial default values for those
   * settings which differ from the original.
   *
   * @param settings Main settings containing the common code style settings.
   * @return Created instance of <code>CommonCodeStyleSettings</code> or null if associated language doesn't
   *         use its own language-specific common settings (the settings are shared with other languages).
   */
  @Nullable
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    return null;
  }

  @NotNull
  public static Language[] getLanguagesWithCodeStyleSettings() {
    ArrayList<Language> langs = new ArrayList<Language>();
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      langs.add(provider.getLanguage());
    }
    return langs.toArray(new Language[langs.size()]);
  }

  @Nullable
  public static String getCodeSample(Language lang, @NotNull SettingsType settingsType) {
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (provider.getLanguage().equals(lang)) {
        return provider.getCodeSample(settingsType);
      }
    }
    return null;
  }

  @Nullable
  public static Language getLanguage(String langName) {
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (langName.equals(provider.getLanguage().getDisplayName())) {
        return provider.getLanguage();
      }
    }
    return null;
  }
}
