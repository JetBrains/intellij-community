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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 5/30/2014
 */
public abstract class ExternalSystemBeforeRunTaskProvider extends BeforeRunTaskProvider<ExternalSystemBeforeRunTask> {

  private static final Logger LOG = Logger.getInstance(ExternalSystemBeforeRunTaskProvider.class);

  @NotNull private final ProjectSystemId mySystemId;
  @NotNull private final Project myProject;
  @NotNull private final Key<ExternalSystemBeforeRunTask> myId;

  public ExternalSystemBeforeRunTaskProvider(@NotNull ProjectSystemId systemId,
                                             @NotNull Project project,
                                             @NotNull Key<ExternalSystemBeforeRunTask> id) {
    mySystemId = systemId;
    myProject = project;
    myId = id;
  }

  @NotNull
  public Key<ExternalSystemBeforeRunTask> getId() {
    return myId;
  }

  @Override
  public String getName() {
    return ExternalSystemBundle.message("tasks.before.run.empty", mySystemId.getReadableName());
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull ExternalSystemBeforeRunTask task) {
    ExternalSystemEditTaskDialog dialog = new ExternalSystemEditTaskDialog(myProject, task.getTaskExecutionSettings(), mySystemId);
    dialog.setTitle(ExternalSystemBundle.message("tasks.select.task.title", mySystemId.getReadableName()));

    if (!dialog.showAndGet()) {
      return false;
    }

    return true;
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull ExternalSystemBeforeRunTask beforeRunTask) {
    final ExternalSystemTaskExecutionSettings executionSettings = beforeRunTask.getTaskExecutionSettings();

    final List<ExternalTaskPojo> tasks = ContainerUtilRt.newArrayList();
    for (String taskName : executionSettings.getTaskNames()) {
      tasks.add(new ExternalTaskPojo(taskName, executionSettings.getExternalProjectPath(), null));
    }
    if (tasks.isEmpty()) return true;


    ExecutionEnvironment environment =
      ExternalSystemUtil.createExecutionEnvironment(myProject, mySystemId, executionSettings, DefaultRunExecutor.EXECUTOR_ID);
    if (environment == null) return false;

    return environment.getRunner().canRun(DefaultRunExecutor.EXECUTOR_ID, environment.getRunProfile());
  }

  @Override
  public boolean executeTask(DataContext context,
                             RunConfiguration configuration,
                             ExecutionEnvironment env,
                             ExternalSystemBeforeRunTask beforeRunTask) {

    final ExternalSystemTaskExecutionSettings executionSettings = beforeRunTask.getTaskExecutionSettings();

    final List<ExternalTaskPojo> tasks = ContainerUtilRt.newArrayList();
    for (String taskName : executionSettings.getTaskNames()) {
      tasks.add(new ExternalTaskPojo(taskName, executionSettings.getExternalProjectPath(), null));
    }
    if (tasks.isEmpty()) return true;

    ExecutionEnvironment environment =
      ExternalSystemUtil.createExecutionEnvironment(myProject, mySystemId, executionSettings, DefaultRunExecutor.EXECUTOR_ID);
    if (environment == null) return false;

    final ProgramRunner runner = environment.getRunner();
    environment.setExecutionId(env.getExecutionId());

    return RunConfigurationBeforeRunProvider.doRunTask(DefaultRunExecutor.getRunExecutorInstance().getId(), environment, runner);
  }

  @Override
  public String getDescription(ExternalSystemBeforeRunTask task) {
    final String externalProjectPath = task.getTaskExecutionSettings().getExternalProjectPath();

    if (task.getTaskExecutionSettings().getTaskNames().isEmpty()) {
      return ExternalSystemBundle.message("tasks.before.run.empty", mySystemId.getReadableName());
    }

    String desc = StringUtil.join(task.getTaskExecutionSettings().getTaskNames(), " ");
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(mySystemId, module)) continue;

      if (StringUtil.equals(externalProjectPath, ExternalSystemApiUtil.getExternalProjectPath(module))) {
        desc = module.getName() + ": " + desc;
        break;
      }
    }

    return ExternalSystemBundle.message("tasks.before.run", mySystemId.getReadableName(), desc);
  }
}
