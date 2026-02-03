// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.PaintAwarePanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public class ExternalSystemEditTaskDialog extends DialogWrapper {

  private final @NotNull ExternalSystemTaskExecutionSettings myTaskExecutionSettings;
  private final @NotNull ExternalSystemTaskSettingsControl myControl;
  private @Nullable JComponent contentPane;
  private final @NotNull Project myProject;

  public ExternalSystemEditTaskDialog(@NotNull Project project,
                                      @NotNull ExternalSystemTaskExecutionSettings taskExecutionSettings,
                                      @NotNull ProjectSystemId externalSystemId) {
    super(project, true);
    myProject = project;
    myTaskExecutionSettings = taskExecutionSettings;

    setTitle(ExternalSystemBundle.message("tasks.edit.task.title", externalSystemId.getReadableName()));
    myControl = new ExternalSystemTaskSettingsControl(project, externalSystemId);
    myControl.setOriginalSettings(taskExecutionSettings);
    setModal(true);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    if (contentPane == null) {
      contentPane = new PaintAwarePanel(new GridBagLayout());
      myControl.fillUi((PaintAwarePanel)contentPane, 0);
      myControl.reset(myProject);
    }
    return contentPane;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @Override
  protected void dispose() {
    super.dispose();
    myControl.disposeUIResources();
  }

  @Override
  protected void doOKAction() {
    myControl.apply(myTaskExecutionSettings);
    super.doOKAction();
  }
}
