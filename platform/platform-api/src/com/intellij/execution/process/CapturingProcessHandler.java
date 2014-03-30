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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

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

  public CapturingProcessHandler(final Process process) {
    this(process, null, "");
  }

  public CapturingProcessHandler(final Process process, final Charset charset) {
    this(process, charset, "");
  }

  public CapturingProcessHandler(final Process process, final Charset charset, final String commandLine) {
    super(process, commandLine, charset);
    addProcessListener(createProcessAdapter(myOutput));
  }

  protected CapturingProcessAdapter createProcessAdapter(ProcessOutput processOutput) {
    return new CapturingProcessAdapter(processOutput);
  }
  
  public ProcessOutput runProcess() {
    startNotify();
    if (waitFor()) {
      myOutput.setExitCode(getProcess().exitValue());
    }
    else {
      LOG.info("runProcess: exit value unavailable");
    }
    return myOutput;
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
    if (timeoutInMilliseconds <= 0) {
      return runProcess();
    }
    else {
      startNotify();
      if (waitFor(timeoutInMilliseconds)) {
        myOutput.setExitCode(getProcess().exitValue());
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
    if (myCharset != null) {
      return myCharset;
    }
    return super.getCharset();
  }

  @NotNull
  public ProcessOutput runProcessWithProgressIndicator(@NotNull ProgressIndicator indicator) {
    startNotify();
    while (!waitFor(100)) {
      if (indicator.isCanceled()) {
        if (!isProcessTerminating() && !isProcessTerminated()) {
          destroyProcess();
        }
        break;
      }
    }
    if (waitFor()) {
      myOutput.setExitCode(getProcess().exitValue());
    }
    else {
      LOG.info("runProcess: exit value unavailable");
    }
    return myOutput;
  }
}