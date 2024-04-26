// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
public final class RunnerRegistry {
  public static @NotNull RunnerRegistry getInstance() {
    return ApplicationManager.getApplication().getService(RunnerRegistry.class);
  }

  /**
   * @deprecated Use {@link ProgramRunner#getRunner(String, RunProfile)}
   */
  @Deprecated
  public @Nullable ProgramRunner getRunner(@NotNull String executorId, @Nullable RunProfile settings) {
    return settings == null ? null : ProgramRunner.getRunner(executorId, settings);
  }
}
