/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO[spLeaner]: eliminate
public class RunnerRegistryImpl extends RunnerRegistry {
  @Override
  public boolean hasRunner(@NotNull String executorId, @NotNull RunProfile settings) {
    return getRunner(executorId, settings) != null;
  }

  @Override
  public ProgramRunner getRunner(@NotNull String executorId, @Nullable RunProfile settings) {
    if (settings == null) {
      return null;
    }

    for (ProgramRunner runner : ProgramRunner.PROGRAM_RUNNER_EP.getExtensions()) {
      if (runner.canRun(executorId, settings)) {
        return runner;
      }
    }
    return null;
  }

  @Override
  public ProgramRunner[] getRegisteredRunners() {
    return ProgramRunner.PROGRAM_RUNNER_EP.getExtensions();
  }

  @Override
  @Nullable
  public ProgramRunner findRunnerById(String id) {
    for (ProgramRunner registeredRunner : ProgramRunner.PROGRAM_RUNNER_EP.getExtensions()) {
      if (Comparing.equal(id, registeredRunner.getRunnerId())) {
        return registeredRunner;
      }
    }
    return null;
  }
}
