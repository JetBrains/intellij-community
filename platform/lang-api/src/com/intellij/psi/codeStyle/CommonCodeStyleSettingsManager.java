/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages common code style settings for every language using them.
 *
 * @author Rustam Vishnyakov
 */
public class CommonCodeStyleSettingsManager {
  private volatile Map<Language, CommonCodeStyleSettings> myCommonSettingsMap;
  private volatile Map<String, Content> myUnknownSettingsMap;

  @NotNull private final CodeStyleSettings myParentSettings;

  @NonNls static final String COMMON_SETTINGS_TAG = "codeStyleSettings";
  private static final String LANGUAGE_ATTR = "language";

  CommonCodeStyleSettingsManager(@NotNull CodeStyleSettings parentSettings) {
    myParentSettings = parentSettings;
  }

  /**
   * Attempts to get language-specific common settings from {@code LanguageCodeStyleSettingsProvider}.
   *
   * @param lang The language to get settings for.
   * @return If the provider for the language exists and is able to create language-specific default settings
   *         ({@code LanguageCodeStyleSettingsProvider.getDefaultCommonSettings()} doesn't return null)
   *         returns the instance of settings for this language. Otherwise returns the instance of parent settings
   *         shared between several languages.
   */
  public CommonCodeStyleSettings getCommonSettings(@Nullable Language lang) {
    Map<Language, CommonCodeStyleSettings> commonSettingsMap = getCommonSettingsMap();
    CommonCodeStyleSettings settings = commonSettingsMap.get(lang);
    while (settings == null && lang != null) {
      lang = lang.getBaseLanguage();
      settings = commonSettingsMap.get(lang);
    }
    if (settings != null) {
      return settings;
    }
    return myParentSettings;
  }

  @NotNull
  private Map<Language, CommonCodeStyleSettings> getCommonSettingsMap() {
    Map<Language, CommonCodeStyleSettings> commonSettingsMap = myCommonSettingsMap;
    if (commonSettingsMap == null) {
      synchronized (this) {
        commonSettingsMap = myCommonSettingsMap;
        if (commonSettingsMap == null) {
          commonSettingsMap = initCommonSettingsMap();
          initNonReadSettings();
        }
      }
    }
    return commonSettingsMap;
  }

  /**
   * Get common code style settings by language name. {@code getCommonSettings(Language)} is a preferred method but
   * sometimes (for example, in plug-ins which do not depend on a specific language support) language settings can be
   * obtained by name.
   * 
   * @param langName The display name of the language whose settings must be returned.
   * @return Common code style settings for the given language or parent (shared) settings if not found.
   */
  @NotNull
  public CommonCodeStyleSettings getCommonSettings(@NotNull String langName) {
    Map<Language, CommonCodeStyleSettings> map = getCommonSettingsMap();
    for (Map.Entry<Language, CommonCodeStyleSettings> entry : map.entrySet()) {
      if (langName.equals(entry.getKey().getDisplayName())) {
        return entry.getValue();
      }
    }
    return myParentSettings;
  }  


  private void initNonReadSettings() {
    final LanguageCodeStyleSettingsProvider[] providers = Extensions.getExtensions(LanguageCodeStyleSettingsProvider.EP_NAME);
    for (final LanguageCodeStyleSettingsProvider provider : providers) {
      Language target = provider.getLanguage();
      if (!myCommonSettingsMap.containsKey(target)) {
        CommonCodeStyleSettings initialSettings = provider.getDefaultCommonSettings();
        if (initialSettings != null) {
          init(initialSettings, target);
        }
      }
    }
  }

  private void init(@NotNull CommonCodeStyleSettings initialSettings, @NotNull Language target) {
    initialSettings.setRootSettings(myParentSettings);
    registerCommonSettings(target, initialSettings);
  }

  private Map<Language, CommonCodeStyleSettings> initCommonSettingsMap() {
    Map<Language, CommonCodeStyleSettings> map = new LinkedHashMap<>();
    myCommonSettingsMap = map;
    myUnknownSettingsMap = new LinkedHashMap<>();
    return map;
  }

  private void registerCommonSettings(@NotNull Language lang, @NotNull CommonCodeStyleSettings settings) {
    synchronized (this) {
      if (!myCommonSettingsMap.containsKey(lang)) {
        myCommonSettingsMap.put(lang, settings);
        settings.getRootSettings(); // check not null
      }
    }
  }

  @NotNull
  public CommonCodeStyleSettingsManager clone(@NotNull CodeStyleSettings parentSettings) {
    synchronized (this) {
      CommonCodeStyleSettingsManager settingsManager = new CommonCodeStyleSettingsManager(parentSettings);
      if (myCommonSettingsMap != null && !myCommonSettingsMap.isEmpty()) {
        settingsManager.initCommonSettingsMap();
        for (Map.Entry<Language, CommonCodeStyleSettings> entry : myCommonSettingsMap.entrySet()) {
          CommonCodeStyleSettings clonedSettings = entry.getValue().clone(parentSettings);
          settingsManager.registerCommonSettings(entry.getKey(), clonedSettings);
        }
        for (Map.Entry<String,Content> contentEntry : myUnknownSettingsMap.entrySet()) {
          Content contentCopy = contentEntry.getValue().clone();
          settingsManager.myUnknownSettingsMap.put(contentEntry.getKey(), contentCopy);
        }
      }
      return settingsManager;
    }
  }

  public void readExternal(@NotNull Element element) throws InvalidDataException {
    synchronized (this) {
      initCommonSettingsMap();
      for (Element commonSettingsElement : element.getChildren(COMMON_SETTINGS_TAG)) {
        final String languageId = commonSettingsElement.getAttributeValue(LANGUAGE_ATTR);
        if (!StringUtil.isEmpty(languageId)) {
          Language target = Language.findLanguageByID(languageId);
          boolean isKnownLanguage = target != null;
          if (isKnownLanguage) {
            final LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(target);
            if (provider != null) {
              CommonCodeStyleSettings settings = provider.getDefaultCommonSettings();
              if (settings != null) {
                settings.readExternal(commonSettingsElement);
                init(settings, target);
              }
            }
            else {
              isKnownLanguage = false;
            }
          }
          if (!isKnownLanguage) {
            myUnknownSettingsMap.put(languageId, commonSettingsElement.clone());
          }
        }
      }
      initNonReadSettings();
    }
  }

  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    synchronized (this) {
      if (myCommonSettingsMap == null) {
        return;
      }

      final Map<String, Language> idToLang = new THashMap<>();
      for (Language language : myCommonSettingsMap.keySet()) {
        idToLang.put(language.getID(), language);
      }

      String[] languages = ArrayUtil.toStringArray(ContainerUtil.union(myUnknownSettingsMap.keySet(), idToLang.keySet()));
      Arrays.sort(languages);
      for (String id : languages) {
        final Language language = idToLang.get(id);
        if (language != null) {
          final CommonCodeStyleSettings commonSettings = myCommonSettingsMap.get(language);
          Element commonSettingsElement = new Element(COMMON_SETTINGS_TAG);
          commonSettings.writeExternal(commonSettingsElement);
          commonSettingsElement.setAttribute(LANGUAGE_ATTR, language.getID());
          if (!commonSettingsElement.getChildren().isEmpty()) {
            element.addContent(commonSettingsElement);
          }
        }
        else {
          final Content unknown = myUnknownSettingsMap.get(id);
          if (unknown != null) {
            element.addContent(unknown.detach());
          }
        }
      }
    }
  }

  public static void copy(@NotNull CommonCodeStyleSettings source, @NotNull CommonCodeStyleSettings target) {
    CommonCodeStyleSettings.copyPublicFields(source, target);
    CommonCodeStyleSettings.IndentOptions targetIndentOptions = target.getIndentOptions();
    if (targetIndentOptions != null) {
      CommonCodeStyleSettings.IndentOptions sourceIndentOptions = source.getIndentOptions();
      if (sourceIndentOptions != null) {
        CommonCodeStyleSettings.copyPublicFields(sourceIndentOptions, targetIndentOptions);
      }
    }
    target.setSoftMargins(source.getSoftMargins());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CommonCodeStyleSettingsManager) {
      CommonCodeStyleSettingsManager other = (CommonCodeStyleSettingsManager)obj;
      if (getCommonSettingsMap().size() != other.getCommonSettingsMap().size() ||
          myUnknownSettingsMap.size() != other.myUnknownSettingsMap.size()) {
        return false;
      }
      for (Language language : myCommonSettingsMap.keySet()) {
        CommonCodeStyleSettings theseSettings = myCommonSettingsMap.get(language);
        CommonCodeStyleSettings otherSettings = other.getCommonSettings(language);
        if (!theseSettings.equals(otherSettings)) return false;
      }
    }
    return true;
  }
}
