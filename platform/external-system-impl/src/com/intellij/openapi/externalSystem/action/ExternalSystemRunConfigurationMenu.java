// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.action;

import com.intellij.execution.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.externalSystem.view.RunConfigurationNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/20/2014
 */
public class ExternalSystemRunConfigurationMenu extends DefaultActionGroup implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    for (AnAction action : getChildActionsOrStubs()) {
      if (action instanceof ExecuteExternalSystemRunConfigurationAction) {
        remove(action);
      }
    }

    final Project project = e.getProject();

    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
    if (selectedNodes == null || selectedNodes.size() != 1 || !(selectedNodes.get(0) instanceof RunConfigurationNode)) return;

    final RunnerAndConfigurationSettings settings = ((RunConfigurationNode)selectedNodes.get(0)).getSettings();

    if (settings == null || project == null) return;

    Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
    for (int i = executors.length; --i >= 0; ) {
      final ProgramRunner runner = ProgramRunnerUtil.getRunner(executors[i].getId(), settings.getConfiguration());
      AnAction action = new ExecuteExternalSystemRunConfigurationAction(executors[i], runner != null, project, settings);
      addAction(action, Constraints.FIRST);
    }

    super.update(e);
  }

  private static class ExecuteExternalSystemRunConfigurationAction extends AnAction {
    private final Executor myExecutor;
    private final boolean myEnabled;
    private final Project myProject;
    private final RunnerAndConfigurationSettings mySettings;

    public ExecuteExternalSystemRunConfigurationAction(Executor executor,
                                                       boolean enabled,
                                                       Project project,
                                                       RunnerAndConfigurationSettings settings) {
      super(executor.getActionName(), null, executor.getIcon());
      myExecutor = executor;
      myEnabled = enabled;
      myProject = project;
      mySettings = settings;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      if (myEnabled) {
        ProgramRunnerUtil.executeConfiguration(mySettings, myExecutor);
        RunManager.getInstance(myProject).setSelectedConfiguration(mySettings);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEnabled);
    }
  }
}
