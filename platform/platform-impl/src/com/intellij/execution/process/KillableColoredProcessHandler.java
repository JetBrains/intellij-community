/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author Roman.Chernyatchik
 *         <p/>
 *         This process handler supports ANSI coloring and soft-kill feature. Soft kill works only on Unix.
 *         At first "stop" button send SIGINT signal to process, if it still hangs user can termintate it recursively with SIGKILL signal.
 *         <p/>
 *         P.S: probably OSProcessHandler is better place for this feature but it can affect other run configurations and should be tested
 */
public class KillableColoredProcessHandler extends ColoredProcessHandler implements KillableProcess {
  private static final Logger LOG = Logger.getInstance(KillableColoredProcessHandler .class);

  private boolean myShouldKillProcessSoftly = true;

  public KillableColoredProcessHandler(GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
  }

  public KillableColoredProcessHandler(final Process process, final String commandLine, @NotNull final Charset charset) {
    super(process, commandLine, charset);
  }

  public KillableColoredProcessHandler(final Process process, final String commandLine) {
    super(process, commandLine);
  }

  /**
   * Sets whether the process will be terminated gracefully.
   * @param shouldKillProcessSoftly true, if graceful process termination should be attempted first (i.e. soft kill)
   */
  public void setShouldKillProcessSoftly(boolean shouldKillProcessSoftly) {
    myShouldKillProcessSoftly = shouldKillProcessSoftly;
  }

  /**
   * This method shouldn't be overridden, see shouldKillProcessSoftly
   *
   * @return
   */
  private boolean canKillProcessSoftly() {
    if (processCanBeKilledByOS(myProcess)) {
      if (SystemInfo.isWindows) {
        // runnerw.exe can send Ctrl+C events to a wrapped process
        return myProcess instanceof RunnerWinProcess;
      }
      else if (SystemInfo.isUnix) {
        // 'kill -SIGINT <pid>' will be executed
        return true;
      }
    }
    return false;
  }

  @Override
  protected void destroyProcessImpl() {
    // Don't close streams, because a process may survive graceful termination.
    // Streams will be closed after the process is really terminated.
    try {
      myProcess.getOutputStream().flush();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    finally {
      doDestroyProcess();
    }
  }

  @Override
  protected void notifyProcessTerminated(int exitCode) {
    try {
      super.closeStreams();
    }
    finally {
      super.notifyProcessTerminated(exitCode);
    }
  }

  @Override
  protected void doDestroyProcess() {
    boolean gracefulTerminationAttempted = false;
    if (canKillProcessSoftly() && shouldKillProcessSoftly()) {
      gracefulTerminationAttempted = destroyProcessGracefully();
    }
    if (!gracefulTerminationAttempted) {
      // execute default process destroy
      super.doDestroyProcess();
    }
  }

  protected boolean destroyProcessGracefully() {
    if (SystemInfo.isWindows) {
      if (myProcess instanceof RunnerWinProcess) {
        RunnerWinProcess runnerWinProcess = (RunnerWinProcess) myProcess;
        runnerWinProcess.destroyGracefully(true);
        return true;
      }
    }
    else if (SystemInfo.isUnix) {
      return UnixProcessManager.sendSigIntToProcessTree(myProcess);
    }
    return false;
  }

  /**
   * @return true, if graceful process termination should be attempted first
   */
  protected boolean shouldKillProcessSoftly() {
    return myShouldKillProcessSoftly;
  }

  @Override
  public boolean canKillProcess() {
    return processCanBeKilledByOS(getProcess());
  }

  @Override
  public void killProcess() {
    // execute 'kill -SIGKILL <pid>' on Unix
    killProcessTree(getProcess());
  }

  @NotNull
  public static KillableColoredProcessHandler create(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    final Process process;
    if (SystemInfo.isWindows) {
      process = RunnerWinProcess.create(commandLine);
    }
    else {
      process = commandLine.createProcess();
    }
    return new KillableColoredProcessHandler(process,
                                             commandLine.getCommandLineString(),
                                             commandLine.getCharset());
  }

}
