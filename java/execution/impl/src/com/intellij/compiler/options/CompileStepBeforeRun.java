// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.task.ProjectTask;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskManager;
import com.intellij.task.impl.EmptyCompileScopeBuildTaskImpl;
import com.intellij.task.impl.ProjectTaskManagerImpl;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class CompileStepBeforeRun extends BeforeRunTaskProvider<CompileStepBeforeRun.MakeBeforeRunTask> implements DumbAware {
  private static final Logger LOG = Logger.getInstance(CompileStepBeforeRun.class);
  public static final Key<MakeBeforeRunTask> ID = Key.create("Make");

  @NonNls private static final String MAKE_PROJECT_ON_RUN_KEY = "makeProjectOnRun";

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
    if (!(configuration instanceof RunProfileWithCompileBeforeLaunchOption runConfiguration)) {
      return true;
    }

    //noinspection deprecation
    if (runConfiguration.isExcludeCompileBeforeLaunchOption() ||
        (configuration instanceof RunConfigurationBase && ((RunConfigurationBase<?>)configuration).excludeCompileBeforeLaunchOption())) {
      return true;
    }

    final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    try {
      final Semaphore done = new Semaphore(1);
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        if (!myProject.isDisposed()) {
          final ProjectTaskManager projectTaskManager = ProjectTaskManager.getInstance(myProject);

          final Pair<ProjectTaskContext, ProjectTask> pair;
          try {
            pair = ReadAction.nonBlocking(() -> {
              final ProjectTask projectTask;
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

                  final Ref<Boolean> includeTests = new Ref<>(true); // use the biggest scope by default
                  if (configuration instanceof JavaRunConfigurationBase) {
                    // use more fine-grained compilation scope avoiding compiling classes, not relevant for running this configuration
                    final JavaRunConfigurationBase conf = (JavaRunConfigurationBase)configuration;
                    final String runClass = conf.getRunClass();
                    final JavaRunConfigurationModule confModule = conf.getConfigurationModule();
                    if (runClass != null && confModule != null) {
                      DumbService.getInstance(confModule.getProject()).runWithAlternativeResolveEnabled(() -> {
                        try {
                          includeTests.set((JavaParametersUtil.getClasspathType(confModule, runClass, false, true) & JavaParameters.TESTS_ONLY) != 0);
                        }
                        catch (CantRunException ignored) {
                        }
                      });
                    }
                  }

                  projectTask = projectTaskManager.createModulesBuildTask(modules, true, true, true, includeTests.get());
                }
                else if (runConfiguration.isBuildProjectOnEmptyModuleList()){
                  projectTask = projectTaskManager.createAllModulesBuildTask(true, myProject);
                }
                else {
                  projectTask = new EmptyCompileScopeBuildTaskImpl(true);
                }
              }
              ProjectTaskContext context = new ProjectTaskContext(ExecutionManagerImpl.EXECUTION_SESSION_ID_KEY.get(env), configuration);
              env.copyUserDataTo(context);
              return Pair.create(context, projectTask);

            }).expireWith(myProject).executeSynchronously();

            ProjectTaskManagerImpl.putBuildOriginator(myProject, CompileStepBeforeRun.class);
            projectTaskManager.run(pair.first, pair.second).onSuccess(taskResult -> {
              if ((!taskResult.hasErrors() || ignoreErrors) && !taskResult.isAborted()) {
                 result.set(Boolean.TRUE);
              }
            }).onProcessed(taskResult -> done.up());
            
          }
          catch (ProcessCanceledException e) {
            done.up();
          }
        }
        else {
          done.up();
        }
      });
      done.waitFor();
    }
    catch (Throwable e) {
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
