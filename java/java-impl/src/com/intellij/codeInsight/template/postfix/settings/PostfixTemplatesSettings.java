/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

@State(
  name = "PostfixTemplatesSettings",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/postfixTemplates.xml")
  }
)
public class PostfixTemplatesSettings implements PersistentStateComponent<PostfixTemplatesSettings>, ExportableComponent {
  @NotNull
  private Map<String, Boolean> myTemplatesState = ContainerUtil.newHashMap();
  private boolean postfixTemplatesEnabled = true;
  private boolean templatesCompletionEnabled = true;
  private int myShortcut = TemplateSettings.TAB_CHAR;

  public boolean isTemplateEnabled(@NotNull PostfixTemplate template) {
    return ContainerUtil.getOrElse(myTemplatesState, template.getKey(), true);
  }

  public void disableTemplate(@NotNull PostfixTemplate template) {
    myTemplatesState.put(template.getKey(), Boolean.FALSE);
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
  public Map<String, Boolean> getTemplatesState() {
    return myTemplatesState;
  }

  public void setTemplatesState(@NotNull Map<String, Boolean> templatesState) {
    myTemplatesState = templatesState;
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
  public PostfixTemplatesSettings getState() {
    return this;
  }

  @Override
  public void loadState(PostfixTemplatesSettings settings) {
    XmlSerializerUtil.copyBean(settings, this);
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
