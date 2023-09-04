// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated Use {@link OSProcessUtil} methods
 */
@Deprecated(forRemoval = true)
public abstract class OSProcessManager {
  public static OSProcessManager getInstance() {
    return ApplicationManager.getApplication().getService(OSProcessManager.class);
  }

  /**
   * @deprecated Use {@link OSProcessUtil#getProcessList()} and then {@link ProcessInfo#getCommandLine()} on items
   */
  @Deprecated(forRemoval = true)
  public abstract @NotNull List<String> getCommandLinesOfRunningProcesses();
}