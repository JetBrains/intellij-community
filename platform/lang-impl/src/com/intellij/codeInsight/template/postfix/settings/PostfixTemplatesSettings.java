// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@State(name = "PostfixTemplatesSettings", storages = @Storage("postfixTemplates.xml"), category = SettingsCategory.CODE)
public final class PostfixTemplatesSettings implements PersistentStateComponent<Element> {
  private Map<String, Set<String>> myProviderToDisabledTemplates = new HashMap<>();
  /**
   * @deprecated use myProviderToDisabledTemplates
   */
  @Deprecated
  private Map<String, Set<String>> myLangToDisabledTemplates = new HashMap<>();

  private boolean postfixTemplatesEnabled = true;
  private boolean templatesCompletionEnabled = true;
  private int myShortcut = TemplateSettings.TAB_CHAR;

  public boolean isTemplateEnabled(@NotNull PostfixTemplate template, @NotNull PostfixTemplateProvider provider) {
    Set<String> result = myProviderToDisabledTemplates.get(provider.getId());
    return result == null || !result.contains(template.getId());
  }

  public void disableTemplate(@NotNull PostfixTemplate template, @NotNull PostfixTemplateProvider provider) {
    disableTemplate(template, provider.getId());
  }

  public void disableTemplate(@NotNull PostfixTemplate template, @NotNull String providerId) {
    Set<String> state = myProviderToDisabledTemplates.computeIfAbsent(providerId, __ -> new HashSet<>());
    state.add(template.getId());
  }

  public boolean isPostfixTemplatesEnabled() {
    return postfixTemplatesEnabled;
  }

  public void setPostfixTemplatesEnabled(boolean postfixTemplatesEnabled) {
    this.postfixTemplatesEnabled = postfixTemplatesEnabled;
  }

  public boolean isTemplatesCompletionEnabled() {
    return templatesCompletionEnabled;
  }

  public void setTemplatesCompletionEnabled(boolean templatesCompletionEnabled) {
    this.templatesCompletionEnabled = templatesCompletionEnabled;
  }

  /**
   * @deprecated use getProviderToDisabledTemplates
   */
  @Deprecated(forRemoval = true)
  @MapAnnotation(entryTagName = "disabled-postfix-templates", keyAttributeName = "lang", surroundWithTag = false)
  public @NotNull Map<String, Set<String>> getLangDisabledTemplates() {
    return myLangToDisabledTemplates;
  }

  /**
   * @deprecated use setProviderToDisabledTemplates
   */
  @Deprecated(forRemoval = true)
  public void setLangDisabledTemplates(@NotNull Map<String, Set<String>> templatesState) {
    myLangToDisabledTemplates = templatesState;
  }

  @MapAnnotation(entryTagName = "disabled-templates", keyAttributeName = "provider", surroundWithTag = false)
  public @NotNull Map<String, Set<String>> getProviderToDisabledTemplates() {
    return myProviderToDisabledTemplates;
  }

  public void setProviderToDisabledTemplates(@NotNull Map<String, Set<String>> templatesState) {
    myProviderToDisabledTemplates = templatesState;
  }

  public int getShortcut() {
    return myShortcut;
  }

  public void setShortcut(int shortcut) {
    myShortcut = shortcut;
  }

  public static @NotNull PostfixTemplatesSettings getInstance() {
    return ApplicationManager.getApplication().getService(PostfixTemplatesSettings.class);
  }

  @Override
  public @Nullable Element getState() {
    Element result = new Element("state");
    XmlSerializer.serializeObjectInto(this, result);
    return result;
  }

  @Override
  public void loadState(@NotNull Element settings) {
    myLangToDisabledTemplates.clear();

    XmlSerializer.deserializeInto(settings, this);

    if (!myLangToDisabledTemplates.isEmpty()) {
      MultiMap<String, Language> importedLanguages = getLanguagesToImport();
      for (Map.Entry<String, Set<String>> entry : myLangToDisabledTemplates.entrySet()) {
        for (Language language : importedLanguages.get(entry.getKey())) {
          for (PostfixTemplateProvider provider : LanguagePostfixTemplate.LANG_EP.allForLanguage(language)) {
            for (PostfixTemplate template : provider.getTemplates()) {
              if (entry.getValue().contains(template.getKey())) {
                disableTemplate(template, provider);
              }
            }
          }
        }
      }
      for (String language : importedLanguages.keySet()) {
        myLangToDisabledTemplates.remove(language);
      }
    }
  }

  private static @NotNull MultiMap<String, Language> getLanguagesToImport() {
    MultiMap<String, Language> importedLanguages = MultiMap.create();
    for (LanguageExtensionPoint extension : LanguagePostfixTemplate.EP_NAME.getExtensionList()) {
      Language language = Language.findLanguageByID(extension.getKey());
      if (language == null) continue;
      importedLanguages.putValue(language.getDisplayName(), language);
    }
    return importedLanguages;
  }
}
