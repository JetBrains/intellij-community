/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

/** @deprecated use {@link KillableColoredProcessHandler#KillableColoredProcessHandler(GeneralCommandLine, boolean)} (to be removed in IDEA 16) */
@SuppressWarnings({"unused", "deprecation"})
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
