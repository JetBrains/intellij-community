// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class OSProcessManager {
  public static OSProcessManager getInstance() {
    return ApplicationManager.getApplication().getService(OSProcessManager.class);
  }

  /**
   * @deprecated Use {@link OSProcessUtil#killProcessTree} directly.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @SuppressWarnings("MethodMayBeStatic")
  public final boolean killProcessTree(@NotNull Process process) {
    return OSProcessUtil.killProcessTree(process);
  }

  @NotNull
  public abstract List<String> getCommandLinesOfRunningProcesses();
}