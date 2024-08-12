// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.fileStatus;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.FileStatusFactory;
import com.intellij.openapi.vcs.FileStatusManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class FileStatusColorsConfigurable implements SearchableConfigurable, Configurable.NoScroll, Configurable.VariableProjectAppLevel {

  private static final String FILE_STATUS_COLORS_ID = "file.status.colors";

  private @Nullable FileStatusColorsPanel myPanel;

  @Override
  public @NotNull String getId() {
    return FILE_STATUS_COLORS_ID;
  }

  @Override
  public String getHelpTopic() {
    return "reference.versionControl.highlight";
  }

  @Override
  public @Nls String getDisplayName() {
    return ApplicationBundle.message("title.file.status.colors");
  }

  @Override
  public @Nullable JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new FileStatusColorsPanel(FileStatusFactory.getInstance().getAllFileStatuses());
    }
    return myPanel.getComponent();
  }

  @Override
  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel = null;
    }
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.getModel().isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPanel != null) {
      myPanel.getModel().apply();
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        FileStatusManager.getInstance(project).fileStatusesChanged();
      }
    }
  }

  @Override
  public void reset() {
    if (myPanel != null) {
      myPanel.getModel().reset();
    }
  }

  @Override
  public boolean isProjectLevel() {
    return false;
  }
}
