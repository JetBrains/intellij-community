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

package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.RunProfileStarter;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GenericProgramRunner<Settings extends RunnerSettings> extends BaseProgramRunner<Settings> {
  @Deprecated
  public static final DataKey<RunContentDescriptor> CONTENT_TO_REUSE_DATA_KEY = DataKey.create("contentToReuse");
  @SuppressWarnings({"UnusedDeclaration", "deprecation"}) @Deprecated @NonNls
  public static final String CONTENT_TO_REUSE = CONTENT_TO_REUSE_DATA_KEY.getName();

  @Override
  protected void startRunProfile(@NotNull ExecutionEnvironment environment, @Nullable final Callback callback, @NotNull Project project, @NotNull RunProfileState state)
    throws ExecutionException {
    ExecutionManager.getInstance(project).startRunProfile(new RunProfileStarter() {
      @Override
      public RunContentDescriptor execute(@NotNull Project project,
                                          @NotNull Executor executor,
                                          @NotNull RunProfileState state,
                                          @Nullable RunContentDescriptor contentToReuse,
                                          @NotNull ExecutionEnvironment environment) throws ExecutionException {
        RunContentDescriptor descriptor = doExecute(project, state, contentToReuse, environment);
        if (descriptor != null) {
          descriptor.setExecutionId(environment.getExecutionId());
        }
        if (callback != null) {
          callback.processStarted(descriptor);
        }
        return descriptor;
      }
    }, state, environment);
  }

  @Nullable
  protected abstract RunContentDescriptor doExecute(@NotNull Project project, @NotNull RunProfileState state,
                                                    @Nullable RunContentDescriptor contentToReuse,
                                                    @NotNull ExecutionEnvironment environment) throws ExecutionException;

}
