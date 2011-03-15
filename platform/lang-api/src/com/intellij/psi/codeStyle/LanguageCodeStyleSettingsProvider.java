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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
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

  public int getRightMargin(@NotNull SettingsType settingsType) {
    return settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS ? 30 : -1;
  }

  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
  }

  /**
   * Override this method if file extension to be used with samples is different from the one returned by associated file type.
   * 
   * @return The file extension for samples (null by default).
   */
  @Nullable
  public String getFileExt() {
    return null;
  }

  /**
   * Override this method if language name shown in preview tab must be different from the name returned by Language class itself.
   * 
   * @return The language name to show in preview tab (null by default).
   */
  @Nullable
  public String getLanguageName() {
    return null;
  }

  /**
   * Allows to customize PSI file creation for a language settings preview panel.
   *
   * @param project current project
   * @param text    code sample to demonstrate formatting settings (see {@link #getCodeSample(LanguageCodeStyleSettingsProvider.SettingsType)}
   * @return a PSI file instance with given text, or null for use
   */
  @Nullable
  public PsiFile createFileFromText(final Project project, final String text) {
    return null;
  }

  /**
   * Creates an instance of <code>CommonCodeStyleSettings</code> and sets initial default values for those
   * settings which differ from the original.
   *
   * @return Created instance of <code>CommonCodeStyleSettings</code> or null if associated language doesn't
   *         use its own language-specific common settings (the settings are shared with other languages).
   */
  @Nullable
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    return null;
  }

  @NotNull
  public static Language[] getLanguagesWithCodeStyleSettings() {
    final ArrayList<Language> languages = new ArrayList<Language>();
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      languages.add(provider.getLanguage());
    }
    return languages.toArray(new Language[languages.size()]);
  }

  @Nullable
  public static String getCodeSample(Language lang, @NotNull SettingsType settingsType) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    return provider != null ? provider.getCodeSample(settingsType) : null;
  }

  public static int getRightMargin(Language lang, @NotNull SettingsType settingsType) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    return provider != null ? provider.getRightMargin(settingsType) : -1;
  }

  @Nullable
  public static Language getLanguage(String langName) {
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      String name = provider.getLanguageName();
      if (name == null) name = provider.getLanguage().getDisplayName();
      if (langName.equals(name)) {
        return provider.getLanguage();
      }
    }
    return null;
  }

  @Nullable
  public static CommonCodeStyleSettings getDefaultCommonSettings(Language lang) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    return provider != null ? provider.getDefaultCommonSettings() : null;
  }
  
  @Nullable
  public static String getFileExt(Language lang) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    return provider != null ? provider.getFileExt() : null;
  }
  
  @Nullable
  public static String getLanguageName(Language lang) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(lang);
    return provider != null ? provider.getLanguageName() : null;
  }

  @Nullable
  public static PsiFile createFileFromText(final Language language, final Project project, final String text) {
    final LanguageCodeStyleSettingsProvider provider = forLanguage(language);
    return provider != null ? provider.createFileFromText(project, text) : null;
  }

  @Nullable
  public static LanguageCodeStyleSettingsProvider forLanguage(final Language language) {
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (provider.getLanguage().equals(language)) {
        return provider;
      }
    }
    return null;
  }
}
