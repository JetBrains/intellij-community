// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.task.ProjectTask;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskManager;
import com.intellij.task.impl.EmptyCompileScopeBuildTaskImpl;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class CompileStepBeforeRun extends BeforeRunTaskProvider<CompileStepBeforeRun.MakeBeforeRunTask> {
  private static final Logger LOG = Logger.getInstance(CompileStepBeforeRun.class);
  public static final Key<MakeBeforeRunTask> ID = Key.create("Make");
  /**
   * @deprecated to be removed in IDEA 2020.1
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated public static final Key<RunConfiguration> RUN_CONFIGURATION = CompilerManager.RUN_CONFIGURATION_KEY;
  /**
   * @deprecated to be removed in IDEA 2020.1
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated public static final Key<String> RUN_CONFIGURATION_TYPE_ID = CompilerManager.RUN_CONFIGURATION_TYPE_ID_KEY;

  @NonNls protected static final String MAKE_PROJECT_ON_RUN_KEY = "makeProjectOnRun";

  private final Project myProject;

  public CompileStepBeforeRun(@NotNull final Project project) {
    myProject = project;
  }

  @Override
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

  @Override
  @Nullable
  public MakeBeforeRunTask createTask(@NotNull RunConfiguration configuration) {
    MakeBeforeRunTask task = null;
    if (shouldCreateTask(configuration)) {
      task = new MakeBeforeRunTask();
      task.setEnabled(isEnabledByDefault(configuration));
    }
    return task;
  }

  private static boolean isEnabledByDefault(@NotNull RunConfiguration configuration) {
    if (configuration instanceof RunProfileWithCompileBeforeLaunchOption) {
      return ((RunProfileWithCompileBeforeLaunchOption)configuration).isBuildBeforeLaunchAddedByDefault();
    }
    else {
      return false;
    }
  }

  static boolean shouldCreateTask(RunConfiguration configuration) {
    return !(configuration instanceof RemoteConfiguration) && configuration instanceof RunProfileWithCompileBeforeLaunchOption;
  }

  @Override
  public boolean executeTask(@NotNull DataContext context, @NotNull final RunConfiguration configuration, @NotNull final ExecutionEnvironment env, @NotNull MakeBeforeRunTask task) {
    return doMake(myProject, configuration, env, false);
  }

  static boolean doMake(final Project myProject, final RunConfiguration configuration, final ExecutionEnvironment env, final boolean ignoreErrors) {
    return doMake(myProject, configuration, env, ignoreErrors, Boolean.getBoolean(MAKE_PROJECT_ON_RUN_KEY));
  }

  static boolean doMake(final Project myProject, final RunConfiguration configuration, final ExecutionEnvironment env, final boolean ignoreErrors, final boolean forceMakeProject) {
    if (!(configuration instanceof RunProfileWithCompileBeforeLaunchOption)) {
      return true;
    }
    
    final RunProfileWithCompileBeforeLaunchOption runConfiguration = (RunProfileWithCompileBeforeLaunchOption)configuration;
    //noinspection deprecation
    if (runConfiguration.isExcludeCompileBeforeLaunchOption() ||
        (configuration instanceof RunConfigurationBase && ((RunConfigurationBase)configuration).excludeCompileBeforeLaunchOption())) {
      return true;
    }

    final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    try {
      Semaphore done = new Semaphore(1);
      ApplicationManager.getApplication().invokeLater(() -> {
        final ProjectTask projectTask;
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
          else if (runConfiguration.isBuildProjectOnEmptyModuleList()){
            projectTask = projectTaskManager.createAllModulesBuildTask(true, myProject);
          }
          else {
            projectTask = new EmptyCompileScopeBuildTaskImpl(true);
          }
        }

        if (!myProject.isDisposed()) {
          ProjectTaskContext context = new ProjectTaskContext(sessionId, configuration);
          env.copyUserDataTo(context);
          projectTaskManager
            .run(context, projectTask)
            .onSuccess(taskResult -> {
              if ((!taskResult.hasErrors() || ignoreErrors) && !taskResult.isAborted()) {
                result.set(Boolean.TRUE);
              }
            })
            .onProcessed(taskResult -> done.up());
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
