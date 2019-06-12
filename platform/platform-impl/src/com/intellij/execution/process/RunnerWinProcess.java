// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

/** @deprecated use {@link KillableColoredProcessHandler#KillableColoredProcessHandler(GeneralCommandLine, boolean)} (to be removed in IDEA 16) */
@Deprecated
@SuppressWarnings({"unused"})
public class RunnerWinProcess extends ProcessWrapper {

  private RunnerWinProcess(@NotNull Process originalProcess) {
    super(originalProcess);
  }

  /**
   * Sends Ctrl+C or Ctrl+Break event to the process.
   * @param softKill if true, Ctrl+C event will be sent (otherwise, Ctrl+Break)
   */
  public void destroyGracefully(boolean softKill) {
    RunnerMediator.destroyProcess(this, softKill);
  }

  @NotNull
  public static Process create(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    if (!SystemInfo.isWindows) {
      throw new RuntimeException("RunnerWinProcess works on Windows only!");
    }
    boolean success = RunnerMediator.injectRunnerCommand(commandLine, false);
    Process process = commandLine.createProcess();
    return success ? new RunnerWinProcess(process) : process;
  }
}
