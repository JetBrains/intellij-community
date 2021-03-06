// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandlerFactory;
import com.intellij.execution.process.ProcessTerminatedListener;
import org.jetbrains.annotations.NotNull;

public final class JavaCommandLineStateUtil {
  private JavaCommandLineStateUtil() { }

  @NotNull
  public static OSProcessHandler startProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return startProcess(commandLine, false);
  }

  @NotNull
  public static OSProcessHandler startProcess(@NotNull GeneralCommandLine commandLine, boolean ansiColoring) throws ExecutionException {
    ProcessHandlerFactory factory = ProcessHandlerFactory.getInstance();
    OSProcessHandler processHandler = ansiColoring ?
                                      factory.createColoredProcessHandler(commandLine) :
                                      factory.createProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }
}
