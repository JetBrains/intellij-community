// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.jetbrains.annotations.NotNull;

public final class ProcessHandlerFactoryImpl extends ProcessHandlerFactory {

  @Override
  public @NotNull OSProcessHandler createProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new OSProcessHandler(commandLine);
  }

  @Override
  public @NotNull OSProcessHandler createColoredProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new ColoredProcessHandler(commandLine);
  }
}
