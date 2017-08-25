/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 5/26/13 11:20 PM
 */
public class ExternalSystemTaskRunner extends GenericProgramRunner {
  public static final JComponent EMPTY_COMPONENT = new JComponent() {
  };

  @NotNull
  @Override
  public String getRunnerId() {
    return ExternalSystemConstants.RUNNER_ID;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return profile instanceof ExternalSystemRunConfiguration && DefaultRunExecutor.EXECUTOR_ID.equals(executorId);
  }

  @Nullable
  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env) throws ExecutionException {
    ExecutionResult executionResult = state.execute(env.getExecutor(), this);
    if (executionResult == null) {
      return null;
    }
    else {
      return new RunContentDescriptor(executionResult.getExecutionConsole(), executionResult.getProcessHandler(),
                                      EMPTY_COMPONENT, env.getRunProfile().getName(), null) {
        @Override
        public boolean isHiddenContent() {
          return true;
        }
      };
    }
  }
}
