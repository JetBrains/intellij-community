/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.io.InputStream;
import java.io.OutputStream;

public class RunnerWinProcess extends Process {

  private final Process myOriginalProcess;

  private RunnerWinProcess(@NotNull Process originalProcess) {
    myOriginalProcess = originalProcess;
  }

  @Override
  public OutputStream getOutputStream() {
    return myOriginalProcess.getOutputStream();
  }

  @Override
  public InputStream getInputStream() {
    return myOriginalProcess.getInputStream();
  }

  @Override
  public InputStream getErrorStream() {
    return myOriginalProcess.getErrorStream();
  }

  @Override
  public int waitFor() throws InterruptedException {
    return myOriginalProcess.waitFor();
  }

  @Override
  public int exitValue() {
    return myOriginalProcess.exitValue();
  }

  @Override
  public void destroy() {
    myOriginalProcess.destroy();
  }

  public Process getOriginalProcess() {
    return myOriginalProcess;
  }

  /**
   * Sends Ctrl+C or Ctrl+Break event to the process.
   * @param softKill if true, Ctrl+C event will be sent (otherwise, Ctrl+Break)
   */
  public void destroyGracefully(boolean softKill) {
    RunnerMediator.destroyProcess(this, softKill);
  }

  @NotNull
  public static RunnerWinProcess create(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    if (!SystemInfo.isWindows) {
      throw new RuntimeException(RunnerWinProcess.class.getSimpleName() + " works on Windows only!");
    }
    RunnerMediator.injectRunnerCommand(commandLine);
    Process process = commandLine.createProcess();
    return new RunnerWinProcess(process);
  }

}
