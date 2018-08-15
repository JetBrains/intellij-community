// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RunnerRegistry {
  @NotNull
  public static RunnerRegistry getInstance() {
    return ServiceManager.getService(RunnerRegistry.class);
  }

  @Nullable
  public abstract ProgramRunner getRunner(@NotNull String executorId, @Nullable RunProfile settings);

  @Nullable
  public abstract ProgramRunner findRunnerById(@NotNull String id);
}
