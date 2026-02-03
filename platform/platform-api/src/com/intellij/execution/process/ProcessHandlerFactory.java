// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public abstract class ProcessHandlerFactory {

  public static ProcessHandlerFactory getInstance() {
    return ApplicationManager.getApplication().getService(ProcessHandlerFactory.class);
  }

  /**
   * Returns a new instance of the {@link OSProcessHandler}.
   */
  public abstract @NotNull OSProcessHandler createProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException;

  /**
   * Returns a new instance of the {@link OSProcessHandler} which is aware of ANSI coloring output.
   */
  public abstract @NotNull OSProcessHandler createColoredProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException;

}
