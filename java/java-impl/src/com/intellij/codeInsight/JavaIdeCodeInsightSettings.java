// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "JavaIdeCodeInsightSettings", storages = @Storage("codeInsightSettings.xml"), category = SettingsCategory.CODE, perClient = true)
public class JavaIdeCodeInsightSettings implements PersistentStateComponent<JavaIdeCodeInsightSettings>, OptionContainer {

  @XCollection(propertyElementName = "included-static-names", elementName = "name", valueAttributeName = "")
  public List<String> includedAutoStaticNames = new ArrayList<>();

  public static JavaIdeCodeInsightSettings getInstance() {
    return ApplicationManager.getApplication().getService(JavaIdeCodeInsightSettings.class);
  }

  @Override
  public @Nullable JavaIdeCodeInsightSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JavaIdeCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    String autoStaticImportMessage =
      JavaBundle.message("auto.static.import.comment.ide");
    return OptPane.pane(
      OptPane.stringList("includedAutoStaticNames", autoStaticImportMessage));
  }


  /**
   * Provides bindId = "JavaIdeCodeInsightSettings.includedAutoStaticNames" to control auto-imports
   */
  public static final class Provider implements OptionControllerProvider {
    @Override
    public @NotNull OptionController forContext(@NotNull PsiElement context) {
      Project project = context.getProject();
      return getInstance().getOptionController()
        .onValueSet((bindId, value) ->
                      DaemonCodeAnalyzerEx.getInstanceEx(project).restart("JavaIdeCodeInsightSettings.Provider.forContext")
        );
    }

    @Override
    public @NotNull String name() {
      return "JavaIdeCodeInsightSettings";
    }
  }
}
