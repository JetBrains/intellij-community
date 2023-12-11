// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.options;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class MakeProjectStepBeforeRun extends BeforeRunTaskProvider<MakeProjectStepBeforeRun.MakeProjectBeforeRunTask>
  implements DumbAware {
  public static final Key<MakeProjectBeforeRunTask> ID = Key.create("MakeProject");

  private final Project myProject;

  public MakeProjectStepBeforeRun(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Key<MakeProjectBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return ExecutionBundle.message("before.launch.make.project.step");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Actions.Compile;
  }

  @Override
  public Icon getTaskIcon(MakeProjectBeforeRunTask task) {
    return AllIcons.Actions.Compile;
  }

  @Override
  public boolean executeTask(@NotNull DataContext context, @NotNull final RunConfiguration configuration, @NotNull final ExecutionEnvironment env, @NotNull MakeProjectBeforeRunTask task) {
    return CompileStepBeforeRun.doMake(myProject, configuration, env, false, true);
  }

  @Nullable
  public static RunConfiguration getRunConfiguration(final CompileContext context) {
    return getRunConfiguration(context.getCompileScope());
  }

  @Nullable
  public static RunConfiguration getRunConfiguration(final CompileScope compileScope) {
    return compileScope.getUserData(CompilerManager.RUN_CONFIGURATION_KEY);
  }


  @Override
  public MakeProjectBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    return !(runConfiguration instanceof RemoteConfiguration) && runConfiguration instanceof RunProfileWithCompileBeforeLaunchOption
           ? new MakeProjectBeforeRunTask()
           : null;
  }

  public static class MakeProjectBeforeRunTask extends BeforeRunTask<MakeProjectBeforeRunTask> {
    public MakeProjectBeforeRunTask() {
      super(ID);
    }
  }
}
