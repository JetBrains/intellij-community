// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
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
class CommonCodeStyleSettingsManager {
  private volatile Map<Language, CommonCodeStyleSettings> myCommonSettingsMap;
  private volatile Map<String, Content> myUnknownSettingsMap;

  @NotNull private final CodeStyleSettings myParentSettings;

  @NonNls static final String COMMON_SETTINGS_TAG = "codeStyleSettings";
  private static final String LANGUAGE_ATTR = "language";

  private static final Logger LOG = Logger.getInstance(CommonCodeStyleSettingsManager.class);

  private static class DefaultsHolder {
    private final static CommonCodeStyleSettings SETTINGS = new CommonCodeStyleSettings(Language.ANY);
    static {
      SETTINGS.initIndentOptions();
      SETTINGS.setRootSettings(CodeStyleSettings.getDefaults());
    }
  }

  CommonCodeStyleSettingsManager(@NotNull CodeStyleSettings parentSettings) {
    myParentSettings = parentSettings;
  }

  @Nullable
  CommonCodeStyleSettings getCommonSettings(@Nullable Language lang) {
    Map<Language, CommonCodeStyleSettings> commonSettingsMap = getCommonSettingsMap();
    Language baseLang = ObjectUtils.notNull(lang, Language.ANY);
    while (baseLang != null) {
      CommonCodeStyleSettings settings = commonSettingsMap.get(baseLang);
      if (settings != null) return settings;
      baseLang = baseLang.getBaseLanguage();
    }
    return null;
  }

  CommonCodeStyleSettings getDefaults() {
    return DefaultsHolder.SETTINGS;
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
   * @return Common code style settings for the given language or a new instance with default values if not found.
   */
  @NotNull
  public CommonCodeStyleSettings getCommonSettings(@NotNull String langName) {
    Map<Language, CommonCodeStyleSettings> map = getCommonSettingsMap();
    for (Map.Entry<Language, CommonCodeStyleSettings> entry : map.entrySet()) {
      if (langName.equals(entry.getKey().getDisplayName())) {
        return entry.getValue();
      }
    }
    return new CommonCodeStyleSettings(Language.ANY);
  }


  private void initNonReadSettings() {
    for (final LanguageCodeStyleSettingsProvider provider : LanguageCodeStyleSettingsProvider.EP_NAME.getExtensionList()) {
      Language target = provider.getLanguage();
      if (!myCommonSettingsMap.containsKey(target)) {
        CommonCodeStyleSettings initialSettings = safelyGetDefaults(provider);
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
        // no need to clone, myUnknownSettingsMap contains immutable elements
        settingsManager.myUnknownSettingsMap.putAll(myUnknownSettingsMap);
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
              CommonCodeStyleSettings settings = safelyGetDefaults(provider);
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
            myUnknownSettingsMap.put(languageId, JDOMUtil.internElement(commonSettingsElement));
          }
        }
      }
      initNonReadSettings();
    }
  }

  private static CommonCodeStyleSettings safelyGetDefaults(LanguageCodeStyleSettingsProvider provider) {
    @SuppressWarnings("deprecation")
    Ref<CommonCodeStyleSettings> defaultSettingsRef =
      RecursionManager.doPreventingRecursion(provider, true, () -> Ref.create(provider.getDefaultCommonSettings()));
    if (defaultSettingsRef == null) {
      LOG.error(new ExtensionException(provider.getClass(), new Throwable(provider.getClass().getCanonicalName() + ".getDefaultCommonSettings() recursively creates root settings.")));
      return null;
    }
    else {
      CommonCodeStyleSettings defaultSettings = defaultSettingsRef.get();
      if (defaultSettings instanceof CodeStyleSettings) {
        LOG.error(new ExtensionException(provider.getClass(), new Throwable(provider.getClass().getName() + ".getDefaultCommonSettings() creates root CodeStyleSettings instead of CommonCodeStyleSettings")));
      }
      return defaultSettings;
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

      String[] languages = ArrayUtilRt.toStringArray(ContainerUtil.union(myUnknownSettingsMap.keySet(), idToLang.keySet()));
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
            element.addContent(unknown.clone());
          }
        }
      }
    }
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
      return true;
    }
    return false;
  }

  void removeLanguageSettings(@NotNull Language language) {
    getCommonSettingsMap().remove(language);
  }

  void addLanguageSettings(@NotNull Language language, @NotNull CommonCodeStyleSettings settings) {
    getCommonSettingsMap().put(language, settings);
  }
}
