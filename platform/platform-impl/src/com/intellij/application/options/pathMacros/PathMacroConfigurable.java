// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.pathMacros;

import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class PathMacroConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  public static final @NonNls String HELP_ID = "preferences.pathVariables";
  private PathMacroListEditor myEditor;

  @Override
  public JComponent createComponent() {
    myEditor = new PathMacroListEditor();
    return myEditor.getPanel();
  }

  @Override
  public void apply() throws ConfigurationException {
    myEditor.commit();

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      StorageUtilKt.checkUnknownMacros(project, false);
    }
  }

  @Override
  public void reset() {
    myEditor.reset();
  }

  @Override
  public void disposeUIResources() {
    myEditor = null;
  }

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.path.variables");
  }

  @Override
  public @NotNull String getHelpTopic() {
    return HELP_ID;
  }

  @Override
  public boolean isModified() {
    return myEditor != null && myEditor.isModified();
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }
}
