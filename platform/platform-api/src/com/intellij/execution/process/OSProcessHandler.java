/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.concurrent.Future;

public class OSProcessHandler extends BaseOSProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.OSProcessHandler");
  private boolean myHasPty = false;

  private boolean myDestroyRecursively = true;

  public OSProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    this(commandLine.createProcess(), commandLine.getCommandLineString(), CharsetToolkit.UTF8_CHARSET);
    setHasPty(commandLine instanceof PtyCommandLine);
  }

  public OSProcessHandler(@NotNull final Process process) {
    this(process, null);
  }

  public OSProcessHandler(@NotNull final Process process, @Nullable final String commandLine) {
    this(process, commandLine, EncodingManager.getInstance().getDefaultCharset());
  }

  public OSProcessHandler(@NotNull final Process process, @Nullable final String commandLine, @Nullable final Charset charset) {
    super(process, commandLine, charset);
  }

  protected OSProcessHandler(@NotNull final OSProcessHandler base) {
    this(base.myProcess, base.myCommandLine);
  }

  @Override
  protected Future<?> executeOnPooledThread(Runnable task) {
    final Application application = ApplicationManager.getApplication();

    if (application != null) {
      return application.executeOnPooledThread(task);
    }

    return super.executeOnPooledThread(task);
  }

  protected boolean shouldDestroyProcessRecursively() {
    // Override this method if you want to kill process recursively (whole process try) by default
    // such behaviour is better than default java one, which doesn't kill children processes
    return myDestroyRecursively;
  }

  public void setShouldDestroyProcessRecursively(boolean destroyRecursively) {
    myDestroyRecursively = destroyRecursively;
  }

  @Override
  protected void doDestroyProcess() {
    // Override this method if you want to customize default destroy behaviour, e.g.
    // if you want use some soft-kill.
    final Process process = getProcess();
    if (shouldDestroyProcessRecursively() && processCanBeKilledByOS(process)) {
      killProcessTree(process);
    }
    else {
      process.destroy();
    }
  }

  public static boolean processCanBeKilledByOS(Process process) {
    return !(process instanceof SelfKiller);
  }

  /**
   * Kills the whole process tree asynchronously.
   * As a potentially time-consuming operation, it's executed asynchronously on a pooled thread.
   *
   * @param process Process
   */
  protected void killProcessTree(@NotNull final Process process) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      killProcessTreeSync(process);
    }
    else {
      executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          killProcessTreeSync(process);
        }
      });
    }
  }

  private void killProcessTreeSync(@NotNull Process process) {
    LOG.debug("killing process tree");
    final boolean destroyed = OSProcessManager.getInstance().killProcessTree(process);
    if (!destroyed) {
      if (isTerminated(process)) {
        LOG.warn("Process has been already terminated: " + myCommandLine);
      }
      else {
        LOG.warn("Cannot kill process tree. Trying to destroy process using Java API. Cmdline:\n" + myCommandLine);
        process.destroy();
      }
    }
  }

  private static boolean isTerminated(@NotNull Process process) {
    try {
      process.exitValue();
      return true;
    }
    catch (IllegalThreadStateException e) {
      return false;
    }
  }

  /**
   * In case of pty this process handler will use blocking read. The value should be set before
   * startNotify invocation. It is set by default in case of using GeneralCommandLine based constructor.
   *
   * @param hasPty true if process is pty based
   */
  public void setHasPty(boolean hasPty) {
    myHasPty = hasPty;
  }

  @Override
  protected boolean useNonBlockingRead() {
    if (myHasPty) {
      // blocking read in case of pty based process
      return false;
    }
    else {
      return super.useNonBlockingRead();
    }
  }
}
