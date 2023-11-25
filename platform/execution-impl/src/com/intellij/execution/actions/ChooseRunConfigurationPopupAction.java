// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

public class ChooseRunConfigurationPopupAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {

    Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    DataContext dataContext = IdeUiService.getInstance().createAsyncDataContext(e.getDataContext());
    ChooseRunConfigurationPopup popup = ActionUtil.underModalProgress(project,
                                                                      ExecutionBundle.message("progress.title.preparing.run.configurations"),
                                                                      () -> new ChooseRunConfigurationPopup(dataContext,
                                                                                                            getAdKey(),
                                                                                                            getDefaultExecutor(),
                                                                                                            getAlternativeExecutor()));
    popup.show();
  }

  protected Executor getDefaultExecutor() {
    return DefaultRunExecutor.getRunExecutorInstance();
  }

  protected Executor getAlternativeExecutor() {
    return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
  }

  protected String getAdKey() {
    return "run.configuration.alternate.action.ad";
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = e.getData(CommonDataKeys.PROJECT);

    presentation.setEnabled(true);
    if (project == null || project.isDisposed()) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    if (null == getDefaultExecutor()) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setEnabledAndVisible(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
