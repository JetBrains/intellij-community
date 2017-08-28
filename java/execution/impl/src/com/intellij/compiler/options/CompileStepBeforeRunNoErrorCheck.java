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
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull MakeBeforeRunTaskNoErrorCheck task) {
    return false;
  }

  @Override
  public String getName() {
    return ExecutionBundle.message("before.launch.compile.step.no.error.check");
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull MakeBeforeRunTaskNoErrorCheck task) {
    return true;
  }

  @Override
  public boolean executeTask(DataContext context,
                             RunConfiguration configuration,
                             ExecutionEnvironment env,
                             MakeBeforeRunTaskNoErrorCheck task) {
    return CompileStepBeforeRun.doMake(myProject, configuration, env, true);
  }

  public static class MakeBeforeRunTaskNoErrorCheck extends BeforeRunTask<MakeBeforeRunTaskNoErrorCheck> {
    private MakeBeforeRunTaskNoErrorCheck() {
      super(ID);
    }
  }
}
