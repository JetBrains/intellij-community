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
  name = "PostfixCompletionSettings",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/postfixCompletion.xml")
  }
)
public class PostfixCompletionSettings implements PersistentStateComponent<PostfixCompletionSettings>, ExportableComponent {
  @NotNull
  private Map<String, Boolean> myTemplatesState = ContainerUtil.newHashMap();
  private boolean postfixPluginEnabled = true;
  private boolean templatesCompletionEnabled = true;
  private int myShortcut = TemplateSettings.TAB_CHAR;

  public boolean isTemplateEnabled(@NotNull PostfixTemplate template) {
    return ContainerUtil.getOrElse(myTemplatesState, template.getKey(), true);
  }

  public void disableTemplate(@NotNull PostfixTemplate template) {
    myTemplatesState.put(template.getKey(), Boolean.FALSE);
  }

  public boolean isPostfixPluginEnabled() {
    return postfixPluginEnabled;
  }

  public void setPostfixPluginEnabled(boolean postfixPluginEnabled) {
    this.postfixPluginEnabled = postfixPluginEnabled;
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
  public static PostfixCompletionSettings getInstance() {
    return ServiceManager.getService(PostfixCompletionSettings.class);
  }

  @Nullable
  @Override
  public PostfixCompletionSettings getState() {
    return this;
  }

  @Override
  public void loadState(PostfixCompletionSettings settings) {
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
