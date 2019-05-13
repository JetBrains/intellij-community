// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunnerRegistry {
  @NotNull
  public static RunnerRegistry getInstance() {
    return ServiceManager.getService(RunnerRegistry.class);
  }

  /**
   * Use {@link ProgramRunner#getRunner)}
   */
  @Nullable
  @Deprecated
  public ProgramRunner getRunner(@NotNull String executorId, @Nullable RunProfile settings) {
    return settings == null ? null : ProgramRunner.getRunner(executorId, settings);
  }

  /**
   * Use {@link ProgramRunner#findRunnerById(String)}
   */
  @Nullable
  @Deprecated
  public ProgramRunner findRunnerById(@NotNull String id) {
    return ProgramRunner.findRunnerById(id);
  }
}
