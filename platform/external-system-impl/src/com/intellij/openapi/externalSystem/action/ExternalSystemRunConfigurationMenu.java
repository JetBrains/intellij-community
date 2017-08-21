/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
  public void update(AnActionEvent e) {
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
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executors[i].getId(), settings.getConfiguration());
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
