// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandlerFactory;
import com.intellij.execution.process.ProcessTerminatedListener;
import org.jetbrains.annotations.NotNull;

public final class JavaCommandLineStateUtil {
  private JavaCommandLineStateUtil() { }

  public static @NotNull OSProcessHandler startProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return startProcess(commandLine, false);
  }

  public static @NotNull OSProcessHandler startProcess(@NotNull GeneralCommandLine commandLine, boolean ansiColoring) throws ExecutionException {
    ProcessHandlerFactory factory = ProcessHandlerFactory.getInstance();
    OSProcessHandler processHandler = ansiColoring ?
                                      factory.createColoredProcessHandler(commandLine) :
                                      factory.createProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }
}
