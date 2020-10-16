// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public abstract OSProcessHandler createProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException;

  /**
   * Returns a new instance of the {@link OSProcessHandler} which is aware of ANSI coloring output.
   */
  @NotNull
  public abstract OSProcessHandler createColoredProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException;

}
