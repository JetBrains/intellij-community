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
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages common code style settings for every language using them.
 *
 * @author Rustam Vishnyakov
 */
public class CommonCodeStyleSettingsManager implements JDOMExternalizable {

  private Map<Language, CommonCodeStyleSettings> myCommonSettingsMap;
  private final CodeStyleSettings myParentSettings;

  private static final String COMMON_SETTINGS_TAG = "commonCodeStyleSettings";
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
    if (myCommonSettingsMap == null) initCommonSettings();
    CommonCodeStyleSettings settings = myCommonSettingsMap.get(lang);
    if (settings != null) {
      return settings;
    }
    return myParentSettings;
  }

  private void initCommonSettings() {
    initCommonSettingsMap();
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

  private void initCommonSettingsMap() {
    myCommonSettingsMap = new LinkedHashMap<Language, CommonCodeStyleSettings>();
  }

  private void registerCommonSettings(@NotNull Language lang, @NotNull CommonCodeStyleSettings settings) {
    if (!myCommonSettingsMap.containsKey(lang)) {
      myCommonSettingsMap.put(lang, settings);
    }
  }

  public CommonCodeStyleSettingsManager clone(CodeStyleSettings parentSettings) {
    CommonCodeStyleSettingsManager settingsManager = new CommonCodeStyleSettingsManager(parentSettings);
    if (myCommonSettingsMap != null) {
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
            if (target == null) {
              target = new Language(languageId) {};
            }
            final CommonCodeStyleSettings settings = new CommonCodeStyleSettings(target);
            settings.readExternal(commonSettingsElement);
            registerCommonSettings(target, settings);
          }
        }
      }
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (myCommonSettingsMap == null) return;
    final Language[] languages = myCommonSettingsMap.keySet().toArray(new Language[myCommonSettingsMap.keySet().size()]);
    Arrays.sort(languages, new Comparator<Language>() {
      public int compare(final Language o1, final Language o2) {
        return o1.getDisplayName().compareTo(o2.getDisplayName());
      }
    });

    for (Language language : languages) {
      final CommonCodeStyleSettings commonSettings = myCommonSettingsMap.get(language);
      Element commonSettingsElement = new Element(COMMON_SETTINGS_TAG);
      commonSettings.writeExternal(commonSettingsElement);
      commonSettingsElement.setAttribute(LANGUAGE_ATTR, language.getID());
      if (!element.getContent().isEmpty()) {
        element.addContent(commonSettingsElement);
      }
    }
  }
}
