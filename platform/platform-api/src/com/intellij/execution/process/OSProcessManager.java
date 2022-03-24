// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated Use {@link OSProcessUtil} methods
 */
@Deprecated
public abstract class OSProcessManager {
  public static OSProcessManager getInstance() {
    return ApplicationManager.getApplication().getService(OSProcessManager.class);
  }

  /**
   * @deprecated Use {@link OSProcessUtil#killProcessTree(Process)} directly.
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("MethodMayBeStatic")
  public final boolean killProcessTree(@NotNull Process process) {
    return OSProcessUtil.killProcessTree(process);
  }

  /**
   * @deprecated Use {@link OSProcessUtil#getProcessList()} and then {@link ProcessInfo#getCommandLine()} on items
   */
  @Deprecated
  @NotNull
  public abstract List<String> getCommandLinesOfRunningProcesses();
}