/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Utility class for running an external process and capturing its standard output and error streams.
 *
 * @author yole
 */
public class CapturingProcessHandler extends OSProcessHandler {
  private static final Logger LOG = Logger.getInstance(CapturingProcessHandler.class);

  private final ProcessOutput myOutput = new ProcessOutput();

  public CapturingProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
    addProcessListener(createProcessAdapter(myOutput));
  }

  /** @deprecated Use {@link #CapturingProcessHandler(Process, Charset, String)} instead (to be removed in IDEA 17) */
  @Deprecated
  public CapturingProcessHandler(@NotNull Process process) {
    this(process, null, "");
  }

  /** @deprecated Use {@link #CapturingProcessHandler(Process, Charset, String)} instead (to be removed in IDEA 17) */
  @Deprecated
  public CapturingProcessHandler(@NotNull Process process, @Nullable Charset charset) {
    this(process, charset, "");
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public CapturingProcessHandler(@NotNull Process process, @Nullable Charset charset, /*@NotNull*/ String commandLine) {
    super(process, commandLine, charset);
    addProcessListener(createProcessAdapter(myOutput));
  }

  protected CapturingProcessAdapter createProcessAdapter(ProcessOutput processOutput) {
    return new CapturingProcessAdapter(processOutput);
  }

  @NotNull
  public ProcessOutput runProcess() {
    startNotify();
    if (waitFor()) {
      setErrorCodeIfNotYetSet();
    }
    else {
      LOG.info("runProcess: exit value unavailable");
    }
    return myOutput;
  }

  private void setErrorCodeIfNotYetSet() {
    // if exit code was set on processTerminated, no need to rewrite it
    // WinPtyProcess returns -2 if pty is already closed
    if (!myOutput.isExitCodeSet()) {
      myOutput.setExitCode(getProcess().exitValue());
    }
  }

  /**
   * Starts process with specified timeout
   *
   * @param timeoutInMilliseconds non-positive means infinity
   */
  public ProcessOutput runProcess(int timeoutInMilliseconds) {
    return runProcess(timeoutInMilliseconds, true);
  }

  /**
   * Starts process with specified timeout
   *
   * @param timeoutInMilliseconds non-positive means infinity
   * @param destroyOnTimeout whether to kill the process after timeout passes
   */
  public ProcessOutput runProcess(int timeoutInMilliseconds, boolean destroyOnTimeout) {
    // keep in sync with runProcessWithProgressIndicator
    if (timeoutInMilliseconds <= 0) {
      return runProcess();
    }
    else {
      startNotify();
      if (waitFor(timeoutInMilliseconds)) {
        setErrorCodeIfNotYetSet();
      }
      else {
        if (destroyOnTimeout) {
          destroyProcess();
        }
        myOutput.setTimeout();
      }
      return myOutput;
    }
  }

  @Override
  public Charset getCharset() {
    return myCharset != null ? myCharset : super.getCharset();
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator) {
    return runProcessWithProgressIndicator(indicator, -1);
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator, int timeoutInMilliseconds) {
    return runProcessWithProgressIndicator(indicator, timeoutInMilliseconds, true);
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator, int timeoutInMilliseconds, boolean destroyOnTimeout) {
    // keep in sync with runProcess
    if (timeoutInMilliseconds <= 0) {
      timeoutInMilliseconds = Integer.MAX_VALUE;
    }

    final int WAIT_INTERVAL = 100;
    int waitingTime = 0;
    boolean setExitCode = true;

    startNotify();
    while (!waitFor(WAIT_INTERVAL)) {
      waitingTime += WAIT_INTERVAL;

      boolean timeout = waitingTime >= timeoutInMilliseconds;
      boolean canceled = indicator.isCanceled();

      if (canceled || timeout) {
        boolean destroying = canceled || destroyOnTimeout;
        setExitCode = destroying;

        if (destroying && !isProcessTerminating() && !isProcessTerminated()) {
          destroyProcess();
        }

        if (canceled) {
          myOutput.setCancelled();
        }
        else {
          myOutput.setTimeout();
        }
        break;
      }
    }
    if (setExitCode) {
      if (waitFor()) {
        setErrorCodeIfNotYetSet();
      }
      else {
        LOG.info("runProcess: exit value unavailable");
      }
    }
    return myOutput;
  }
}