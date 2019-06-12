// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.remote.RemoteProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * This process handler supports the "soft-kill" feature (see {@link KillableProcessHandler}).
 * At first "stop" button send SIGINT signal to process, if it still hangs user can terminate it recursively with SIGKILL signal.
 * <p>
 * Soft kill works on Unix, and also on Windows if a mediator process was used.
 */
public class KillableProcessHandler extends OSProcessHandler implements KillableProcess {

  private static final Logger LOG = Logger.getInstance(KillableProcessHandler.class);
  private static final Key<Boolean> MEDIATOR_KEY = Key.create("KillableProcessHandler.Mediator.Process");

  private boolean myShouldKillProcessSoftly = true;
  private final boolean myMediatedProcess;
  private boolean myShouldKillProcessSoftlyWithWinP = SystemInfo.isWin10OrNewer && Registry.is("use.winp.for.graceful.process.termination");

  public KillableProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
    myMediatedProcess = MEDIATOR_KEY.get(commandLine) == Boolean.TRUE;
  }

  /**
   * Starts a process with a {@link RunnerMediator mediator} when {@code withMediator} is set to {@code true} and the platform is Windows.
   */
  public KillableProcessHandler(@NotNull GeneralCommandLine commandLine, boolean withMediator) throws ExecutionException {
    this(mediate(commandLine, withMediator, false));
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public KillableProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine) {
    super(process, commandLine);
    myMediatedProcess = false;
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public KillableProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @NotNull Charset charset) {
    super(process, commandLine, charset);
    myMediatedProcess = false;
  }

  @NotNull
  protected static GeneralCommandLine mediate(@NotNull GeneralCommandLine commandLine, boolean withMediator, boolean showConsole) {
    if (withMediator && SystemInfo.isWindows && MEDIATOR_KEY.get(commandLine) == null) {
      boolean mediatorInjected = RunnerMediator.injectRunnerCommand(commandLine, showConsole);
      MEDIATOR_KEY.set(commandLine, mediatorInjected);
    }
    return commandLine;
  }

  /**
   * @return true, if graceful process termination should be attempted first
   */
  public boolean shouldKillProcessSoftly() {
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
        return myMediatedProcess || myShouldKillProcessSoftlyWithWinP;
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

  /**
   * Enables sending Ctrl+C to a Windows-process on first termination attempt.
   * This is an experimental API which will be removed in future releases once stabilized.
   * Please do not use this API.
   * @param shouldKillProcessSoftlyWithWinP true to use
   */
  @ApiStatus.Experimental
  public void setShouldKillProcessSoftlyWithWinP(boolean shouldKillProcessSoftlyWithWinP) {
    myShouldKillProcessSoftlyWithWinP = shouldKillProcessSoftlyWithWinP;
  }

  protected boolean destroyProcessGracefully() {
    if (SystemInfo.isWindows) {
      if (myMediatedProcess) {
        return RunnerMediator.destroyProcess(myProcess, true);
      }
      if (myShouldKillProcessSoftlyWithWinP && !Registry.is("disable.winp")) {
        try {
          if (!myProcess.isAlive()) {
            OSProcessUtil.logSkippedActionWithTerminatedProcess(myProcess, "destroy", getCommandLine());
            return true;
          }
          return OSProcessUtil.createWinProcess(myProcess).sendCtrlC();
        }
        catch (Throwable e) {
          if (!myProcess.isAlive()) {
            OSProcessUtil.logSkippedActionWithTerminatedProcess(myProcess, "destroy", getCommandLine());
            return true;
          }
          LOG.error("Failed to send Ctrl+C, fallback to default termination: " + getCommandLine(), e);
        }
      }
    }
    else if (SystemInfo.isUnix) {
      return UnixProcessManager.sendSigIntToProcessTree(myProcess);
    }
    return false;
  }

  @Override
  public boolean canKillProcess() {
    return processCanBeKilledByOS(getProcess()) || getProcess() instanceof RemoteProcess;
  }

  @Override
  public void killProcess() {
    if (processCanBeKilledByOS(getProcess())) {
      // execute 'kill -SIGKILL <pid>' on Unix
      killProcessTree(getProcess());
    }
    else if (getProcess() instanceof RemoteProcess) {
      ((RemoteProcess)getProcess()).killProcessTree();
    }
  }
}
