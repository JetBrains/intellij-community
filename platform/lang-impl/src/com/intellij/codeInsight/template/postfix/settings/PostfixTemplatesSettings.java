// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Factory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

@State(name = "PostfixTemplatesSettings", storages = @Storage("postfixTemplates.xml"))
public class PostfixTemplatesSettings implements PersistentStateComponent<Element> {

  public static final Factory<Set<String>> SET_FACTORY = () -> ContainerUtil.newHashSet();
  private Map<String, Set<String>> myLangToDisabledTemplates = ContainerUtil.newHashMap();

  private boolean postfixTemplatesEnabled = true;
  private boolean templatesCompletionEnabled = true;
  private int myShortcut = TemplateSettings.TAB_CHAR;

  public boolean isTemplateEnabled(@NotNull PostfixTemplate template, @NotNull PostfixTemplateProvider provider) {
    String langForProvider = PostfixTemplatesUtils.getLangForProvider(provider);
    return isTemplateEnabled(template, langForProvider);
  }

  public boolean isTemplateEnabled(PostfixTemplate template, @NotNull String strictLangForProvider) {
    Set<String> result = myLangToDisabledTemplates.get(strictLangForProvider);
    return result == null || !result.contains(template.getKey());
  }

  public void disableTemplate(@NotNull PostfixTemplate template, @NotNull PostfixTemplateProvider provider) {
    String langForProvider = PostfixTemplatesUtils.getLangForProvider(provider);
    disableTemplate(template, langForProvider);
  }

  public void disableTemplate(PostfixTemplate template, String langForProvider) {
    Set<String> state = ContainerUtil.getOrCreate(myLangToDisabledTemplates, langForProvider, SET_FACTORY);
    state.add(template.getKey());
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

  @NotNull
  @MapAnnotation(entryTagName = "disabled-postfix-templates", keyAttributeName = "lang", surroundWithTag = false)
  public Map<String, Set<String>> getLangDisabledTemplates() {
    return myLangToDisabledTemplates;
  }

  public void setLangDisabledTemplates(@NotNull Map<String, Set<String>> templatesState) {
    myLangToDisabledTemplates = templatesState;
  }

  public int getShortcut() {
    return myShortcut;
  }

  public void setShortcut(int shortcut) {
    myShortcut = shortcut;
  }

  @Nullable
  public static PostfixTemplatesSettings getInstance() {
    return ServiceManager.getService(PostfixTemplatesSettings.class);
  }

  @Nullable
  @Override
  public Element getState() {
    return XmlSerializer.serialize(this, new SkipDefaultValuesSerializationFilters());
  }

  @Override
  public void loadState(@NotNull Element settings) {
    XmlSerializer.deserializeInto(this, settings);
  }
}
