// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
 */
final class CommonCodeStyleSettingsManager {
  private volatile Map<String, CommonCodeStyleSettings> myCommonSettingsMap;
  private volatile Map<String, Element> myUnknownSettingsMap;

  private final @NotNull CodeStyleSettings myParentSettings;

  static final @NonNls String COMMON_SETTINGS_TAG = "codeStyleSettings";
  private static final String LANGUAGE_ATTR = "language";

  private static final Logger LOG = Logger.getInstance(CommonCodeStyleSettingsManager.class);

  private static class DefaultsHolder {
    private static final CommonCodeStyleSettings SETTINGS = new CommonCodeStyleSettings(Language.ANY);
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
    Map<String, CommonCodeStyleSettings> commonSettingsMap = getCommonSettingsMap();
    Language baseLang = ObjectUtils.notNull(lang, Language.ANY);
    while (baseLang != null) {
      CommonCodeStyleSettings settings = commonSettingsMap.get(baseLang.getID());
      if (settings != null) return settings;
      baseLang = baseLang.getBaseLanguage();
    }
    return null;
  }

  CommonCodeStyleSettings getDefaults() {
    return DefaultsHolder.SETTINGS;
  }

  private @NotNull Map<String, CommonCodeStyleSettings> getCommonSettingsMap() {
    Map<String, CommonCodeStyleSettings> commonSettingsMap = myCommonSettingsMap;
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
  public @NotNull CommonCodeStyleSettings getCommonSettings(@NotNull String langName) {
    Language language = findLanguageByDisplayName(langName);

    if (language != null) {
      Map<String, CommonCodeStyleSettings> map = getCommonSettingsMap();
      CommonCodeStyleSettings settings = map.get(language.getID());
      if (settings != null) {
        return settings;
      }
    }
    return new CommonCodeStyleSettings(Language.ANY);
  }

  private static Language findLanguageByDisplayName(@NotNull String langName) {
    Language l = Language.findLanguageByID(langName); // optimization
    if (l == null || !l.getDisplayName().equals(langName)) {
      for (Language language : Language.getRegisteredLanguages()) {
        if (langName.equals(language.getDisplayName())) {
          l = language;
          break;
        }
      }
    }
    return l;
  }


  private void initNonReadSettings() {
    for (final LanguageCodeStyleProvider provider : CodeStyleSettingsService.getInstance().getLanguageCodeStyleProviders()) {
      Language target = provider.getLanguage();
      if (!myCommonSettingsMap.containsKey(target.getID()) && !provider.useBaseLanguageCommonSettings()) {
        CommonCodeStyleSettings initialSettings = safelyGetDefaults(provider);
        if (initialSettings != null) {
          init(initialSettings, target.getID());
        }
      }
    }
  }

  private void init(@NotNull CommonCodeStyleSettings initialSettings, @NotNull String langId) {
    initialSettings.setRootSettings(myParentSettings);
    registerCommonSettings(langId, initialSettings);
  }

  @NotNull
  private Map<String, CommonCodeStyleSettings> initCommonSettingsMap() {
    Map<String, CommonCodeStyleSettings> map = new LinkedHashMap<>();
    myCommonSettingsMap = map;
    myUnknownSettingsMap = new LinkedHashMap<>();
    return map;
  }

  private void registerCommonSettings(@NotNull String langId, @NotNull CommonCodeStyleSettings settings) {
    synchronized (this) {
      if (!myCommonSettingsMap.containsKey(langId)) {
        myCommonSettingsMap.put(langId, settings);
        //noinspection ResultOfMethodCallIgnored
        settings.getRootSettings(); // check not null
      }
    }
  }

  public @NotNull CommonCodeStyleSettingsManager clone(@NotNull CodeStyleSettings parentSettings) {
    synchronized (this) {
      CommonCodeStyleSettingsManager settingsManager = new CommonCodeStyleSettingsManager(parentSettings);
      if (myCommonSettingsMap != null && !myCommonSettingsMap.isEmpty()) {
        settingsManager.initCommonSettingsMap();
        for (Map.Entry<String, CommonCodeStyleSettings> entry : myCommonSettingsMap.entrySet()) {
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
      CodeStyleSettingsService settingsService = CodeStyleSettingsService.getInstance();
      for (Element commonSettingsElement : element.getChildren(COMMON_SETTINGS_TAG)) {
        final String languageId = commonSettingsElement.getAttributeValue(LANGUAGE_ATTR);
        if (!StringUtil.isEmpty(languageId)) {
          final LanguageCodeStyleProvider provider = ContainerUtil.find(settingsService.getLanguageCodeStyleProviders(),
                                                                        p -> languageId.equals(p.getLanguage().getID()));
          if (provider != null) {
            CommonCodeStyleSettings commonSettings = readExternal(provider, commonSettingsElement);
            if (commonSettings != null) {
              init(commonSettings, provider.getLanguage().getID());
            }
          }
          else {
            myUnknownSettingsMap.put(languageId, JDOMUtil.internElement(commonSettingsElement));
          }
        }
      }
      initNonReadSettings();
    }
  }

  private static @Nullable CommonCodeStyleSettings safelyGetDefaults(LanguageCodeStyleProvider provider) {
    Ref<CommonCodeStyleSettings> defaultSettingsRef =
      RecursionManager.doPreventingRecursion(provider, true, () -> new Ref<>(provider.getDefaultCommonSettings()));
    if (defaultSettingsRef == null) {
      LOG.error(PluginException.createByClass(provider.getClass().getCanonicalName() + ".getDefaultCommonSettings() recursively creates root settings.", null, provider.getClass()));
      return null;
    }
    else {
      CommonCodeStyleSettings defaultSettings = defaultSettingsRef.get();
      if (defaultSettings instanceof CodeStyleSettings) {
        LOG.error(PluginException.createByClass(provider.getClass().getName() + ".getDefaultCommonSettings() creates root CodeStyleSettings instead of CommonCodeStyleSettings", null, provider.getClass()));
      }
      return defaultSettings;
    }
  }

  private static @Nullable CommonCodeStyleSettings readExternal(@NotNull LanguageCodeStyleProvider provider, @NotNull Element commonSettingsElement) {
    CommonCodeStyleSettings settings = safelyGetDefaults(provider);
    if (settings != null) {
      settings.readExternal(commonSettingsElement);
    }
    return settings;
  }

  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    synchronized (this) {
      if (myCommonSettingsMap == null) {
        return;
      }

      String[] langIds = ArrayUtilRt.toStringArray(ContainerUtil.union(myUnknownSettingsMap.keySet(), myCommonSettingsMap.keySet()));
      Arrays.sort(langIds);
      for (String id : langIds) {
        final Language language = Language.findLanguageByID(id);
        if (language != null && myCommonSettingsMap.containsKey(id)) {
          final CommonCodeStyleSettings commonSettings = myCommonSettingsMap.get(id);
          LanguageCodeStyleProvider provider = LanguageCodeStyleProvider.forLanguage(language);
          if (provider != null) {
            Element commonSettingsElement = writeCommonSettings(id, commonSettings, provider);
            if (!commonSettingsElement.getChildren().isEmpty()) {
              element.addContent(commonSettingsElement);
            }
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

  private static Element writeCommonSettings(@NotNull String langId,
                                             @NotNull CommonCodeStyleSettings commonSettings,
                                             @NotNull LanguageCodeStyleProvider provider) {
    Element commonSettingsElement = new Element(COMMON_SETTINGS_TAG);
    commonSettings.writeExternal(commonSettingsElement, provider);
    commonSettingsElement.setAttribute(LANGUAGE_ATTR, langId);
    return commonSettingsElement;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CommonCodeStyleSettingsManager other) {
      if (getCommonSettingsMap().size() != other.getCommonSettingsMap().size() ||
          myUnknownSettingsMap.size() != other.myUnknownSettingsMap.size()) {
        return false;
      }
      for (String langId : myCommonSettingsMap.keySet()) {
        CommonCodeStyleSettings theseSettings = myCommonSettingsMap.get(langId);
        CommonCodeStyleSettings otherSettings = other.myCommonSettingsMap.get(langId);
        if (!theseSettings.equals(otherSettings)) return false;
      }
      return true;
    }
    return false;
  }

  void removeLanguageSettings(@NotNull LanguageCodeStyleProvider provider) {
    Map<String,CommonCodeStyleSettings> settingsMap = getCommonSettingsMap();
    String langId = provider.getLanguage().getID();
    CommonCodeStyleSettings commonSettings = settingsMap.get(langId);
    if (commonSettings != null) {
      Element serialized = writeCommonSettings(langId, settingsMap.get(langId), provider);
      if (!serialized.getChildren().isEmpty()) {
        myUnknownSettingsMap.put(langId, JDOMUtil.internElement(serialized));
      }
      settingsMap.remove(langId);
    }
  }

  void addLanguageSettings(@NotNull LanguageCodeStyleProvider provider) {
    getCommonSettingsMap(); // Initialize if needed
    String langId = provider.getLanguage().getID();
    CommonCodeStyleSettings commonSettings;
    if (myUnknownSettingsMap.containsKey(langId)) {
      commonSettings = readExternal(provider, myUnknownSettingsMap.get(langId));
      if (commonSettings != null) {
        myUnknownSettingsMap.remove(langId);
      }
    }
    else {
      commonSettings = safelyGetDefaults(provider);
    }
    if (commonSettings != null) {
      init(commonSettings, langId);
    }
  }
}
