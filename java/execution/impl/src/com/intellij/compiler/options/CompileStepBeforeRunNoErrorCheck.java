// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.options;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CompileStepBeforeRunNoErrorCheck
  extends BeforeRunTaskProvider<CompileStepBeforeRunNoErrorCheck.MakeBeforeRunTaskNoErrorCheck> {
  public static final Key<MakeBeforeRunTaskNoErrorCheck> ID = Key.create("MakeNoErrorCheck");
  @NotNull private final Project myProject;

  public CompileStepBeforeRunNoErrorCheck(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Key<MakeBeforeRunTaskNoErrorCheck> getId() {
    return ID;
  }

  @Override
  public String getDescription(MakeBeforeRunTaskNoErrorCheck task) {
    return ExecutionBundle.message("before.launch.compile.step.no.error.check");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Actions.Compile;
  }

  @Override
  public Icon getTaskIcon(MakeBeforeRunTaskNoErrorCheck task) {
    return AllIcons.Actions.Compile;
  }

  @Override
  public MakeBeforeRunTaskNoErrorCheck createTask(@NotNull RunConfiguration runConfiguration) {
    return CompileStepBeforeRun.shouldCreateTask(runConfiguration) ? new MakeBeforeRunTaskNoErrorCheck() : null;
  }

  @Override
  public String getName() {
    return ExecutionBundle.message("before.launch.compile.step.no.error.check");
  }

  @Override
  public boolean executeTask(@NotNull DataContext context,
                             @NotNull RunConfiguration configuration,
                             @NotNull ExecutionEnvironment env,
                             @NotNull MakeBeforeRunTaskNoErrorCheck task) {
    return CompileStepBeforeRun.doMake(myProject, configuration, env, true);
  }

  public static final class MakeBeforeRunTaskNoErrorCheck extends BeforeRunTask<MakeBeforeRunTaskNoErrorCheck> {
    private MakeBeforeRunTaskNoErrorCheck() {
      super(ID);
    }
  }
}
