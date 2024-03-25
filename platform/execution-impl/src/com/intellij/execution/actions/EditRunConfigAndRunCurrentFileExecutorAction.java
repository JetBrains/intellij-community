// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.impl.RunDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class EditRunConfigAndRunCurrentFileExecutorAction extends RunCurrentFileExecutorAction {
  public EditRunConfigAndRunCurrentFileExecutorAction(@NotNull Executor executor) {
    super(executor);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    presentation.setText(ExecutionBundle.message("run.configurations.popup.edit.run.config.and.run.current.file"));
    presentation.setDescription(ExecutionBundle.message("run.configurations.popup.edit.run.config.and.run.current.file.description"));
    presentation.setIcon(AllIcons.Actions.EditSource);
  }

  @Override
  protected void doRunCurrentFile(@NotNull Project project,
                                  @NotNull RunnerAndConfigurationSettings runConfig,
                                  @NotNull DataContext dataContext) {
    String suggestedName = StringUtil.notNullize(((LocatableConfiguration)runConfig.getConfiguration()).suggestedName(),
                                                 runConfig.getName());
    List<String> usedNames = ContainerUtil.map(RunManager.getInstance(project).getAllSettings(), RunnerAndConfigurationSettings::getName);
    String uniqueName = UniqueNameGenerator.generateUniqueName(suggestedName, "", "", " (", ")", s -> !usedNames.contains(s));
    runConfig.setName(uniqueName);

    String dialogTitle = ExecutionBundle.message("dialog.title.edit.configuration.settings");
    if (RunDialog.editConfiguration(project, runConfig, dialogTitle, myExecutor)) {
      RunManager.getInstance(project).setTemporaryConfiguration(runConfig);
      super.doRunCurrentFile(project, runConfig, dataContext);
    }
  }
}
