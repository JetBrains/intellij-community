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
import com.intellij.execution.configurations.RunProfileWithCompileBeforeLaunchOption;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MakeProjectStepBeforeRun extends BeforeRunTaskProvider<MakeProjectStepBeforeRun.MakeProjectBeforeRunTask> {
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

  public boolean executeTask(DataContext context, final RunConfiguration configuration, final ExecutionEnvironment env, MakeProjectBeforeRunTask task) {
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
