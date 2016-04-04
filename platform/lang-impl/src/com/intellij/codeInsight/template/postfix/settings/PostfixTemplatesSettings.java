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
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.Factory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Set;

@State(name = "PostfixTemplatesSettings", storages = @Storage("postfixTemplates.xml"))
public class PostfixTemplatesSettings implements PersistentStateComponent<Element>, ExportableComponent {

  public static final Factory<Set<String>> SET_FACTORY = new Factory<Set<String>>() {
    @Override
    public Set<String> create() {
      return ContainerUtil.newHashSet();
    }
  };
  private Map<String, Set<String>> myLangToDisabledTemplates = ContainerUtil.newHashMap();

  private boolean postfixTemplatesEnabled = true;
  private boolean templatesCompletionEnabled = true;
  private int myShortcut = TemplateSettings.TAB_CHAR;

  @Deprecated
  @NotNull
  private Map<String, Boolean> myTemplatesState = ContainerUtil.newHashMap();

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  @NotNull
  public Map<String, Boolean> getTemplatesState() {
    return myTemplatesState;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  public void setTemplatesState(@NotNull Map<String, Boolean> templatesState) {
    myTemplatesState = templatesState;
  }

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
  public void loadState(Element settings) {
    XmlSerializer.deserializeInto(this, settings);

    //Backward compatibility for java
    //old settings were stored in "templatesState" field without language
    //todo remove. for backward compatibility
    if (!myTemplatesState.isEmpty()) {
      myLangToDisabledTemplates.put("JAVA", ContainerUtil.newHashSet(myTemplatesState.keySet()));
      myTemplatesState.clear();
    }
  }

  @NotNull
  @Override
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("postfixCompletion.xml")};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Postfix Completion";
  }
}
