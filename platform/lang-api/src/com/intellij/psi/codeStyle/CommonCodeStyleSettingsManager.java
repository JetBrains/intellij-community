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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages common code style settings for every language using them.
 *
 * @author Rustam Vishnyakov
 */
public class CommonCodeStyleSettingsManager {

  private Map<Language, CommonCodeStyleSettings> myCommonSettingsMap;
  private final CommonCodeStyleSettings myParentSettings;

  public CommonCodeStyleSettingsManager(CommonCodeStyleSettings parentSettings) {
    myParentSettings = parentSettings;
  }

  /**
   * Attempts to get language-specific common settings from <code>LanguageCodeStyleSettingsProvider</code>.
   *
   * @param lang The language to get settings for.
   * @return If the provider for the language exists and is able to create language-specific default settings
   *         (<code>LanguageCodeStyleSettingsProvider.getDefaultCommonSettings()</code> doesn't return null)
   *         returns the instance of settings for this language. Otherwise returns the instance of parent settings
   *         shared between several languages.
   */
  public CommonCodeStyleSettings getCommonSettings(Language lang) {
    if (myCommonSettingsMap == null) initCommonSettings();
    CommonCodeStyleSettings settings = myCommonSettingsMap.get(lang);
    if (settings != null) {
      return settings;
    }
    return myParentSettings;
  }

  private void initCommonSettings() {
    myCommonSettingsMap = new LinkedHashMap<Language, CommonCodeStyleSettings>();
    final LanguageCodeStyleSettingsProvider[] providers = Extensions.getExtensions(LanguageCodeStyleSettingsProvider.EP_NAME);
    for (final LanguageCodeStyleSettingsProvider provider : providers) {
      if (!myCommonSettingsMap.containsKey(provider.getLanguage())) {
        CommonCodeStyleSettings initialSettings = provider.getDefaultCommonSettings();
        if (initialSettings != null) {
          registerCommonSettings(provider.getLanguage(), initialSettings);
        }
      }
    }
  }

  private void registerCommonSettings(@NotNull Language lang, @NotNull CommonCodeStyleSettings settings) {
    if (!myCommonSettingsMap.containsKey(lang)) {
      myCommonSettingsMap.put(lang, settings);
    }
  }
}
