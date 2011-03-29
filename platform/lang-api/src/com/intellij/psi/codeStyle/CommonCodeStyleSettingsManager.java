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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages common code style settings for every language using them.
 *
 * @author Rustam Vishnyakov
 */
public class CommonCodeStyleSettingsManager implements JDOMExternalizable {

  private Map<Language, CommonCodeStyleSettings> myCommonSettingsMap = null;
  private Map<String, Content> myUnknownSettingsMap;

  private final CodeStyleSettings myParentSettings;

  private static final String COMMON_SETTINGS_TAG = "codeStyleSettings";
  private static final String LANGUAGE_ATTR = "language";


  public CommonCodeStyleSettingsManager(CodeStyleSettings parentSettings) {
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
    if (myCommonSettingsMap == null) {
      initCommonSettingsMap();
      initNonReadSettings();
    }
    CommonCodeStyleSettings settings = myCommonSettingsMap.get(lang);
    if (settings != null) {
      return settings;
    }
    return myParentSettings;
  }

  /**
   * Get common code style settings by language name. <code>getCommonSettings(Language)</code> is a preferred method but
   * sometimes (for example, in plug-ins which do not depend on a specific language support) language settings can be
   * obtained by name.
   * 
   * @param langName The display name of the language whose settings must be returned.
   * @return Common code style settings for the given language or parent (shared) settings if not found.
   */
  @NotNull
  public CommonCodeStyleSettings getCommonSettings(@NotNull String langName) {
    if (myCommonSettingsMap == null) {
      initCommonSettingsMap();
      initNonReadSettings();
    }
    for (Language lang : myCommonSettingsMap.keySet()) {
      if (langName.equals(lang.getDisplayName())) {
        return myCommonSettingsMap.get(lang);
      }
    }
    return myParentSettings;
  }  


  private void initNonReadSettings() {
    final LanguageCodeStyleSettingsProvider[] providers = Extensions.getExtensions(LanguageCodeStyleSettingsProvider.EP_NAME);
    for (final LanguageCodeStyleSettingsProvider provider : providers) {
      if (!myCommonSettingsMap.containsKey(provider.getLanguage())) {
        CommonCodeStyleSettings initialSettings = provider.getDefaultCommonSettings();
        if (initialSettings != null) {
          initialSettings.copyNonDefaultValuesFrom(myParentSettings);
          registerCommonSettings(provider.getLanguage(), initialSettings);
        }
      }
    }
  }

  private void initCommonSettingsMap() {
    myCommonSettingsMap = new LinkedHashMap<Language, CommonCodeStyleSettings>();
    myUnknownSettingsMap = new LinkedHashMap<String, Content>();
  }

  private void registerCommonSettings(@NotNull Language lang, @NotNull CommonCodeStyleSettings settings) {
    if (!myCommonSettingsMap.containsKey(lang)) {
      myCommonSettingsMap.put(lang, settings);
    }
  }

  public CommonCodeStyleSettingsManager clone(CodeStyleSettings parentSettings) {
    CommonCodeStyleSettingsManager settingsManager = new CommonCodeStyleSettingsManager(parentSettings);
    if (myCommonSettingsMap != null && myCommonSettingsMap.size() > 0) {
      settingsManager.initCommonSettingsMap();
      for (Map.Entry<Language, CommonCodeStyleSettings> entry : myCommonSettingsMap.entrySet()) {
        settingsManager.registerCommonSettings(entry.getKey(), entry.getValue().clone());
      }
    }
    return settingsManager;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    initCommonSettingsMap();
    final List list = element.getChildren(COMMON_SETTINGS_TAG);
    if (list != null) {
      for(Object o:list) {
        if (o instanceof Element) {
          final Element commonSettingsElement = (Element)o;
          final String languageId = commonSettingsElement.getAttributeValue(LANGUAGE_ATTR);
          if (languageId != null && languageId.length() > 0) {
            Language target = Language.findLanguageByID(languageId);
            if (target != null) {
              final CommonCodeStyleSettings defaultSettings = LanguageCodeStyleSettingsProvider.getDefaultCommonSettings(target);
              final CommonCodeStyleSettings settings = defaultSettings != null ? defaultSettings : new CommonCodeStyleSettings(target);
              settings.readExternal(commonSettingsElement);
              registerCommonSettings(target, settings);
            } else {
              myUnknownSettingsMap.put(languageId, (Content)commonSettingsElement.clone());
            }
          }
        }
      }
    }
    initNonReadSettings();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (myCommonSettingsMap == null) return;

    final Map<String, Language> id2lang = new HashMap<String, Language>();
    for (final Language language : myCommonSettingsMap.keySet()) {
      id2lang.put(language.getID(), language);
    }

    final Set<String> langIdList = new HashSet<String>();
    langIdList.addAll(myUnknownSettingsMap.keySet());
    langIdList.addAll(id2lang.keySet());

    final String[] languages = langIdList.toArray(new String[langIdList.size()]);
    Arrays.sort(languages, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o1.compareTo(o2);
      }
    });

    for (final String id : languages) {
      final Language language = id2lang.get(id);
      if (language != null) {
        final CommonCodeStyleSettings commonSettings = myCommonSettingsMap.get(language);
        Element commonSettingsElement = new Element(COMMON_SETTINGS_TAG);
        commonSettings.writeExternal(commonSettingsElement);
        commonSettingsElement.setAttribute(LANGUAGE_ATTR, language.getID());
        if (!commonSettingsElement.getChildren().isEmpty()) {
          element.addContent(commonSettingsElement);
        }
      } else {
        final Content unknown = myUnknownSettingsMap.get(id);
        if (unknown != null) element.addContent(unknown.detach());
      }
    }
  }
}
