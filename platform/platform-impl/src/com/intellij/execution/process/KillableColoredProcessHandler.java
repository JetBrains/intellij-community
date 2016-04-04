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
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * This process handler supports ANSI coloring and "soft-kill" feature.
 * At first "stop" button send SIGINT signal to process, if it still hangs user can terminate it recursively with SIGKILL signal.
 * <p>
 * Soft kill works on Unix, and also on Windows if a mediator process was used.
 *
 * @author Roman.Chernyatchik
 */
public class KillableColoredProcessHandler extends ColoredProcessHandler implements KillableProcess {
  private static final Logger LOG = Logger.getInstance(KillableColoredProcessHandler.class);
  private static final Key<Boolean> MEDIATOR_KEY = Key.create("KillableColoredProcessHandler.Mediator.Process");

  private boolean myShouldKillProcessSoftly = true;
  private boolean myMediatedProcess = false;

  public KillableColoredProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
  }

  /**
   * Starts a process with a {@link RunnerMediator mediator} when {@code withMediator} is set to {@code true} and the platform is Windows.
   */
  public KillableColoredProcessHandler(@NotNull GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
    super(mediate(commandLine, withMediator));
    myMediatedProcess = withMediator && MEDIATOR_KEY.get(commandLine) == Boolean.TRUE;
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public KillableColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine) {
    super(process, commandLine);
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public KillableColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @NotNull Charset charset) {
    super(process, commandLine, charset);
  }

  private static GeneralCommandLine mediate(GeneralCommandLine commandLine, boolean withMediator) {
    if (withMediator && SystemInfo.isWindows) {
      boolean mediatorInjected = RunnerMediator.injectRunnerCommand(commandLine);
      MEDIATOR_KEY.set(commandLine, mediatorInjected);
    }
    return commandLine;
  }

  /**
   * @return true, if graceful process termination should be attempted first
   */
  protected boolean shouldKillProcessSoftly() {
    return myShouldKillProcessSoftly;
  }

  /**
   * Sets whether the process will be terminated gracefully.
   *
   * @param shouldKillProcessSoftly true, if graceful process termination should be attempted first (i.e. soft kill)
   */
  public void setShouldKillProcessSoftly(boolean shouldKillProcessSoftly) {
    myShouldKillProcessSoftly = shouldKillProcessSoftly;
  }

  /**
   * This method shouldn't be overridden, see shouldKillProcessSoftly
   */
  private boolean canKillProcessSoftly() {
    if (processCanBeKilledByOS(myProcess)) {
      if (SystemInfo.isWindows) {
        // runnerw.exe can send Ctrl+C events to a wrapped process
        return myMediatedProcess;
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
    boolean gracefulTerminationAttempted = shouldKillProcessSoftly() && canKillProcessSoftly() && destroyProcessGracefully();
    if (!gracefulTerminationAttempted) {
      // execute default process destroy
      super.doDestroyProcess();
    }
  }

  protected boolean destroyProcessGracefully() {
    if (SystemInfo.isWindows && myMediatedProcess) {
      return RunnerMediator.destroyProcess(myProcess, true);
    }
    else if (SystemInfo.isUnix) {
      return UnixProcessManager.sendSigIntToProcessTree(myProcess);
    }
    return false;
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

  /** @deprecated use {@link #KillableColoredProcessHandler(GeneralCommandLine, boolean)} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  public static KillableColoredProcessHandler create(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new KillableColoredProcessHandler(commandLine, true);
  }
}