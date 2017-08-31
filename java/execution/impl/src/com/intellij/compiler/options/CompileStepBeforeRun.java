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
package com.intellij.compiler.options;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.task.*;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class CompileStepBeforeRun extends BeforeRunTaskProvider<CompileStepBeforeRun.MakeBeforeRunTask> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.options.CompileStepBeforeRun");
  public static final Key<MakeBeforeRunTask> ID = Key.create("Make");
  /**
   * @deprecated to be removed in IDEA 2017
   */
  public static final Key<RunConfiguration> RUN_CONFIGURATION = CompilerManager.RUN_CONFIGURATION_KEY;
  /**
   * @deprecated to be removed in IDEA 2017
   */
  public static final Key<String> RUN_CONFIGURATION_TYPE_ID = CompilerManager.RUN_CONFIGURATION_TYPE_ID_KEY;

  @NonNls protected static final String MAKE_PROJECT_ON_RUN_KEY = "makeProjectOnRun";

  private final Project myProject;

  public CompileStepBeforeRun(@NotNull final Project project) {
    myProject = project;
  }

  public Key<MakeBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return ExecutionBundle.message("before.launch.compile.step");
  }

  @Override
  public String getDescription(MakeBeforeRunTask task) {
    return ExecutionBundle.message("before.launch.compile.step");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Actions.Compile;
  }

  @Override
  public Icon getTaskIcon(MakeBeforeRunTask task) {
    return AllIcons.Actions.Compile;
  }

  @Nullable
  public MakeBeforeRunTask createTask(@NotNull RunConfiguration configuration) {
    MakeBeforeRunTask task = null;
    if (shouldCreateTask(configuration)) {
      task = new MakeBeforeRunTask();
      if (configuration instanceof RunConfigurationBase) {
        task.setEnabled(((RunConfigurationBase)configuration).isCompileBeforeLaunchAddedByDefault());
      }
    }
    return task;
  }

  static boolean shouldCreateTask(RunConfiguration configuration) {
    return !(configuration instanceof RemoteConfiguration) && configuration instanceof RunProfileWithCompileBeforeLaunchOption;
  }

  public boolean executeTask(DataContext context, @NotNull final RunConfiguration configuration, @NotNull final ExecutionEnvironment env, @NotNull MakeBeforeRunTask task) {
    return doMake(myProject, configuration, env, false);
  }

  static boolean doMake(final Project myProject, final RunConfiguration configuration, final ExecutionEnvironment env, final boolean ignoreErrors) {
    return doMake(myProject, configuration, env, ignoreErrors, Boolean.getBoolean(MAKE_PROJECT_ON_RUN_KEY));
  }

  static boolean doMake(final Project myProject, final RunConfiguration configuration, final ExecutionEnvironment env, final boolean ignoreErrors, final boolean forceMakeProject) {
    if (!(configuration instanceof RunProfileWithCompileBeforeLaunchOption)) {
      return true;
    }

    if (configuration instanceof RunConfigurationBase && ((RunConfigurationBase)configuration).excludeCompileBeforeLaunchOption()) {
      return true;
    }

    final RunProfileWithCompileBeforeLaunchOption runConfiguration = (RunProfileWithCompileBeforeLaunchOption)configuration;
    final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    try {

      final Semaphore done = new Semaphore();
      done.down();
      final ProjectTaskNotification callback = new ProjectTaskNotification() {
        public void finished(@NotNull ProjectTaskResult executionResult) {
          if ((executionResult.getErrors() == 0 || ignoreErrors) && !executionResult.isAborted()) {
            result.set(Boolean.TRUE);
          }
          done.up();
        }
      };

      TransactionGuard.submitTransaction(myProject, () -> {
        ProjectTask projectTask;
        Object sessionId = ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.get(env);
        final ProjectTaskManager projectTaskManager = ProjectTaskManager.getInstance(myProject);
        if (forceMakeProject) {
          // user explicitly requested whole-project make
          projectTask = projectTaskManager.createAllModulesBuildTask(true, myProject);
        }
        else {
          final Module[] modules = runConfiguration.getModules();
          if (modules.length > 0) {
            for (Module module : modules) {
              if (module == null) {
                LOG.error("RunConfiguration should not return null modules. Configuration=" + runConfiguration.getName() + "; class=" +
                          runConfiguration.getClass().getName());
              }
            }
            projectTask = projectTaskManager.createModulesBuildTask(modules, true, true, true);
          }
          else {
            projectTask = projectTaskManager.createAllModulesBuildTask(true, myProject);
          }
        }

        if (!myProject.isDisposed()) {
          projectTaskManager.run(new ProjectTaskContext(sessionId, configuration), projectTask, callback);
        }
        else {
          done.up();
        }
      });
      done.waitFor();
    }
    catch (Exception e) {
      return false;
    }

    return result.get();
  }

  @Nullable
  public static RunConfiguration getRunConfiguration(final CompileContext context) {
    return getRunConfiguration(context.getCompileScope());
  }

  @Nullable
  public static RunConfiguration getRunConfiguration(final CompileScope compileScope) {
    return compileScope.getUserData(CompilerManager.RUN_CONFIGURATION_KEY);
  }

  public static class MakeBeforeRunTask extends BeforeRunTask<MakeBeforeRunTask> {
    public MakeBeforeRunTask() {
      super(ID);
      setEnabled(true);
    }
  }
}
