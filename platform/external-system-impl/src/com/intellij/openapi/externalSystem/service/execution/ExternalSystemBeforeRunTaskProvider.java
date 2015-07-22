/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
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
  public boolean configureTask(RunConfiguration runConfiguration, ExternalSystemBeforeRunTask task) {
    ExternalSystemEditTaskDialog dialog = new ExternalSystemEditTaskDialog(myProject, task.getTaskExecutionSettings(), mySystemId);
    dialog.setTitle(ExternalSystemBundle.message("tasks.select.task.title", mySystemId.getReadableName()));

    if (!dialog.showAndGet()) {
      return false;
    }

    return true;
  }

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, ExternalSystemBeforeRunTask beforeRunTask) {
    final ExternalSystemTaskExecutionSettings executionSettings = beforeRunTask.getTaskExecutionSettings();

    final List<ExternalTaskPojo> tasks = ContainerUtilRt.newArrayList();
    for (String taskName : executionSettings.getTaskNames()) {
      tasks.add(new ExternalTaskPojo(taskName, executionSettings.getExternalProjectPath(), null));
    }
    if (tasks.isEmpty()) return true;

    final Pair<ProgramRunner, ExecutionEnvironment> pair =
      ExternalSystemUtil.createRunner(executionSettings, DefaultRunExecutor.EXECUTOR_ID, myProject, mySystemId);

    if (pair == null) return false;

    final ProgramRunner runner = pair.first;
    final ExecutionEnvironment environment = pair.second;

    return runner.canRun(DefaultRunExecutor.EXECUTOR_ID, environment.getRunProfile());
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

    final Pair<ProgramRunner, ExecutionEnvironment> pair =
      ExternalSystemUtil.createRunner(executionSettings, DefaultRunExecutor.EXECUTOR_ID, myProject, mySystemId);

    if (pair == null) return false;

    final ProgramRunner runner = pair.first;
    final ExecutionEnvironment environment = pair.second;
    environment.setExecutionId(env.getExecutionId());

    final Semaphore targetDone = new Semaphore();
    final Ref<Boolean> result = new Ref<Boolean>(false);
    final Disposable disposable = Disposer.newDisposable();

    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final String executorId = executor.getId();

    myProject.getMessageBus().connect(disposable).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionAdapter() {
      public void processStartScheduled(final String executorIdLocal, final ExecutionEnvironment environmentLocal) {
        if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
          targetDone.down();
        }
      }

      public void processNotStarted(final String executorIdLocal, @NotNull final ExecutionEnvironment environmentLocal) {
        if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
          targetDone.up();
        }
      }

      public void processStarted(final String executorIdLocal,
                                 @NotNull final ExecutionEnvironment environmentLocal,
                                 @NotNull final ProcessHandler handler) {
        if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
          handler.addProcessListener(new ProcessAdapter() {
            public void processTerminated(ProcessEvent event) {
              result.set(event.getExitCode() == 0);
              targetDone.up();
              environmentLocal.getContentToReuse();
            }
          });
        }
      }
    });

    try {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          try {
            runner.execute(environment);
          }
          catch (ExecutionException e) {
            targetDone.up();
            LOG.error(e);
          }
        }
      }, ModalityState.NON_MODAL);
    }
    catch (Exception e) {
      LOG.error(e);
      Disposer.dispose(disposable);
      return false;
    }

    targetDone.waitFor();
    Disposer.dispose(disposable);

    return result.get();
  }

  @Override
  public String getDescription(ExternalSystemBeforeRunTask task) {
    final String externalProjectPath = task.getTaskExecutionSettings().getExternalProjectPath();

    if (task.getTaskExecutionSettings().getTaskNames().isEmpty()) {
      return ExternalSystemBundle.message("tasks.before.run.empty", mySystemId.getReadableName());
    }

    String desc = StringUtil.join(task.getTaskExecutionSettings().getTaskNames(), " ");
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (!mySystemId.toString().equals(module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY))) continue;

      if (StringUtil.equals(externalProjectPath, module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY))) {
        desc = module.getName() + ": " + desc;
        break;
      }
    }

    return ExternalSystemBundle.message("tasks.before.run", mySystemId.getReadableName(), desc);
  }
}
