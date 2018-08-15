// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ProgramRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunnerRegistryImpl extends RunnerRegistry {
  @Override
  @Nullable
  public ProgramRunner getRunner(@NotNull String executorId, @Nullable RunProfile settings) {
    return settings == null ? null : ProgramRunnerUtil.getRunner(executorId, settings);
  }

  @Override
  @Nullable
  public ProgramRunner findRunnerById(@NotNull String id) {
    for (ProgramRunner registeredRunner : ProgramRunner.PROGRAM_RUNNER_EP.getExtensionList()) {
      if (id.equals(registeredRunner.getRunnerId())) {
        return registeredRunner;
      }
    }
    return null;
  }
}
