/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager.ExternalProjectsStateProvider;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 10/28/2014
 */
public class ExternalSystemTaskActivator {

  public static final String RUN_CONFIGURATION_TASK_PREFIX = "run: ";
  @NotNull private final Project myProject;
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public ExternalSystemTaskActivator(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static String getRunConfigurationActivationTaskName(@NotNull RunnerAndConfigurationSettings settings) {
    return RUN_CONFIGURATION_TASK_PREFIX + settings.getName();
  }

  public void init() {
    CompilerManager compilerManager = CompilerManager.getInstance(myProject);

    class MyCompileTask implements CompileTask {
      private final boolean myBefore;

      MyCompileTask(boolean before) {
        myBefore = before;
      }

      @Override
      public boolean execute(CompileContext context) {
        return doExecuteCompileTasks(myBefore, context);
      }
    }

    compilerManager.addBeforeTask(new MyCompileTask(true));
    compilerManager.addAfterTask(new MyCompileTask(false));

    fireTasksChanged();
  }

  public String getDescription(ProjectSystemId systemId, String projectPath, String taskName) {
    List<String> result = new ArrayList<String>();
    final ExternalProjectsStateProvider stateProvider =
      ExternalProjectsManager.getInstance(myProject).getStateProvider();
    final TaskActivationState taskActivationState = stateProvider.getTasksActivation(systemId, projectPath);
    if (taskActivationState == null) return null;

    for (Phase phase : Phase.values()) {
      if (taskActivationState.getTasks(phase).contains(taskName)) {
        result.add(ExternalSystemBundle.message(phase.myMessageKey));
      }
    }
    return StringUtil.join(result, ", ");
  }

  private boolean doExecuteCompileTasks(boolean myBefore, @NotNull CompileContext context) {
    final List<String> modules = ContainerUtil.map(context.getCompileScope().getAffectedModules(), new Function<Module, String>() {
      @Override
      public String fun(Module module) {
        return ExternalSystemApiUtil.getExternalProjectPath(module);
      }
    });

    final Collection<Phase> phases = ContainerUtil.newArrayList();
    if (myBefore) {
      if(context.isRebuild()) {
        phases.add(Phase.BEFORE_REBUILD);
      }
      phases.add(Phase.BEFORE_COMPILE);
    }
    else {
      phases.add(Phase.AFTER_COMPILE);
      if(context.isRebuild()) {
        phases.add(Phase.AFTER_REBUILD);
      }
    }
    return runTasks(modules, ArrayUtil.toObjectArray(phases, Phase.class));
  }

  public boolean runTasks(@NotNull String modulePath, @NotNull Phase... phases) {
    return runTasks(Collections.singleton(modulePath), phases);
  }

  public boolean runTasks(@NotNull Collection<String> modules, @NotNull Phase... phases) {
    final ExternalProjectsStateProvider stateProvider = ExternalProjectsManager.getInstance(myProject).getStateProvider();

    final Queue<Pair<ProjectSystemId, ExternalSystemTaskExecutionSettings>> tasksQueue =
      new LinkedList<Pair<ProjectSystemId, ExternalSystemTaskExecutionSettings>>();

    //noinspection MismatchedQueryAndUpdateOfCollection
    Map<ProjectSystemId, Map<String, RunnerAndConfigurationSettings>> lazyConfigurationsMap =
      new FactoryMap<ProjectSystemId, Map<String, RunnerAndConfigurationSettings>>() {
        @Nullable
        @Override
        protected Map<String, RunnerAndConfigurationSettings> create(ProjectSystemId key) {
          final AbstractExternalSystemTaskConfigurationType configurationType =
            ExternalSystemUtil.findConfigurationType(key);
          if (configurationType == null) return null;
          return ContainerUtil.map2Map(RunManager.getInstance(myProject).getConfigurationSettingsList(configurationType),
                                       new Function<RunnerAndConfigurationSettings, Pair<String, RunnerAndConfigurationSettings>>() {
                                         @Override
                                         public Pair<String, RunnerAndConfigurationSettings> fun(RunnerAndConfigurationSettings configurationSettings) {
                                           return Pair.create(configurationSettings.getName(), configurationSettings);
                                         }
                                       });
        }
      };

    for (final ExternalProjectsStateProvider.TasksActivation activation : stateProvider.getAllTasksActivation()) {
      final boolean hashPath = modules.contains(activation.projectPath);

      final Set<String> tasks = ContainerUtil.newLinkedHashSet();
      for (Phase phase : phases) {
        if(hashPath || phase.isSyncPhase())
        ContainerUtil.addAll(tasks, activation.state.getTasks(phase));
      }

      if (tasks.isEmpty()) continue;

      for (Iterator<String> iterator = tasks.iterator(); iterator.hasNext(); ) {
        String task = iterator.next();
        if (task.length() > RUN_CONFIGURATION_TASK_PREFIX.length() && task.startsWith(RUN_CONFIGURATION_TASK_PREFIX)) {
          iterator.remove();
          final String configurationName = task.substring(RUN_CONFIGURATION_TASK_PREFIX.length());

          Map<String, RunnerAndConfigurationSettings> settings = lazyConfigurationsMap.get(activation.systemId);
          if (settings == null) continue;

          RunnerAndConfigurationSettings configurationSettings = settings.get(configurationName);
          if (configurationSettings == null) continue;

          final RunConfiguration runConfiguration = configurationSettings.getConfiguration();
          if (configurationName.equals(configurationSettings.getName()) && runConfiguration instanceof ExternalSystemRunConfiguration) {
            tasksQueue.add(Pair.create(activation.systemId, ((ExternalSystemRunConfiguration)runConfiguration).getSettings()));
          }
        }
      }

      if (tasks.isEmpty()) continue;

      ExternalSystemTaskExecutionSettings executionSettings = new ExternalSystemTaskExecutionSettings();
      executionSettings.setExternalSystemIdString(activation.systemId.toString());
      executionSettings.setExternalProjectPath(activation.projectPath);
      executionSettings.getTaskNames().addAll(tasks);
      tasksQueue.add(Pair.create(activation.systemId, executionSettings));
    }

    return runTasksQueue(tasksQueue);
  }

  private boolean runTasksQueue(final Queue<Pair<ProjectSystemId, ExternalSystemTaskExecutionSettings>> tasksQueue) {
    final Pair<ProjectSystemId, ExternalSystemTaskExecutionSettings> pair = tasksQueue.poll();
    if (pair == null) return true;

    final ProjectSystemId systemId = pair.first;
    final ExternalSystemTaskExecutionSettings executionSettings = pair.getSecond();

    final Semaphore targetDone = new Semaphore();
    targetDone.down();
    final Ref<Boolean> result = new Ref<Boolean>(false);
    ExternalSystemUtil.runTask(executionSettings, DefaultRunExecutor.EXECUTOR_ID, myProject, systemId,
                               new TaskCallback() {
                                 @Override
                                 public void onSuccess() {
                                   result.set(runTasksQueue(tasksQueue));
                                   targetDone.up();
                                 }

                                 @Override
                                 public void onFailure() {
                                   targetDone.up();
                                 }
                               },
                               ProgressExecutionMode.IN_BACKGROUND_ASYNC);
    targetDone.waitFor();
    return result.get();
  }

  public void addListener(Listener l) {
    myListeners.add(l);
  }

  public boolean isTaskOfPhase(@NotNull TaskData taskData, @NotNull Phase phase) {
    final ExternalProjectsStateProvider stateProvider = ExternalProjectsManager.getInstance(myProject).getStateProvider();
    final TaskActivationState taskActivationState =
      stateProvider.getTasksActivation(taskData.getOwner(), taskData.getLinkedExternalProjectPath());
    if (taskActivationState == null) return false;

    return taskActivationState.getTasks(phase).contains(taskData.getName());
  }

  public void addTasks(List<TaskData> tasks, Phase phase) {
    if (tasks.isEmpty()) return;

    final ExternalProjectsStateProvider stateProvider = ExternalProjectsManager.getInstance(myProject).getStateProvider();
    for (TaskData task : tasks) {
      final TaskActivationState taskActivationState = stateProvider.getTasksActivation(task.getOwner(),
                                                                                       task.getLinkedExternalProjectPath());
      taskActivationState.getTasks(phase).add(task.getName());
    }

    fireTasksChanged();
  }

  public void removeTasks(List<TaskData> tasks, Phase phase) {
    if (tasks.isEmpty()) return;

    final ExternalProjectsStateProvider stateProvider = ExternalProjectsManager.getInstance(myProject).getStateProvider();

    for (TaskData task : tasks) {
      final TaskActivationState taskActivationState = stateProvider.getTasksActivation(task.getOwner(),
                                                                                       task.getLinkedExternalProjectPath());
      taskActivationState.getTasks(phase).remove(task.getName());
    }

    fireTasksChanged();
  }

  public void fireTasksChanged() {
    for (Listener each : myListeners) {
      each.tasksActivationChanged();
    }
  }

  public enum Phase {
    BEFORE_SYNC("external.system.task.before.sync"),
    AFTER_SYNC("external.system.task.after.sync"),
    BEFORE_COMPILE("external.system.task.before.compile"),
    AFTER_COMPILE("external.system.task.after.compile"),
    BEFORE_REBUILD("external.system.task.before.rebuild"),
    AFTER_REBUILD("external.system.task.after.rebuild");

    public final String myMessageKey;

    Phase(String messageKey) {
      myMessageKey = messageKey;
    }

    public boolean isSyncPhase () {
      return this == BEFORE_SYNC || this == AFTER_SYNC;
    }
  }

  public interface Listener {
    void tasksActivationChanged();
  }
}
