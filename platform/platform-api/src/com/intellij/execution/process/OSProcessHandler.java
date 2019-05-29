// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.BaseOutputReader;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Set;

public class OSProcessHandler extends BaseOSProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.OSProcessHandler");
  private static final Set<String> REPORTED_EXECUTIONS = ContainerUtil.newConcurrentSet();
  private static final long ALLOWED_TIMEOUT_THRESHOLD = 10;

  public static final Key<Set<File>> DELETE_FILES_ON_TERMINATION = Key.create("OSProcessHandler.FileToDelete");

  private boolean myHasErrorStream = true;
  private boolean myHasPty;
  private boolean myDestroyRecursively = true;
  private Set<File> myFilesToDelete = null;

  public OSProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    this(startProcess(commandLine), commandLine.getCommandLineString(), commandLine.getCharset());
    myHasErrorStream = !commandLine.isRedirectErrorStream();
    myFilesToDelete = commandLine.getUserData(DELETE_FILES_ON_TERMINATION);
  }

  private static Process startProcess(GeneralCommandLine commandLine) throws ExecutionException {
    try {
      return commandLine.createProcess();
    }
    catch (ExecutionException | RuntimeException | Error e) {
      deleteTempFiles(commandLine.getUserData(DELETE_FILES_ON_TERMINATION));
      throw e;
    }
  }

  @Override
  public boolean waitFor() {
    checkEdtAndReadAction(this);
    return super.waitFor();
  }

  @Override
  public boolean waitFor(long timeoutInMilliseconds) {
    if (timeoutInMilliseconds > ALLOWED_TIMEOUT_THRESHOLD) {
      checkEdtAndReadAction(this);
    }
    return super.waitFor(timeoutInMilliseconds);
  }

  /**
   * Checks if we are going to wait for {@code processHandler} to finish on EDT or under ReadAction. Logs error if we do so.
   * <br/><br/>
   * HOW-TO fix an error from this method:
   * <ul>
   * <li>You are on the pooled thread under {@link com.intellij.openapi.application.ReadAction ReadAction}:
   * <ul>
   *     <li>Synchronous (you need to return execution result or derived information to the caller) - get rid the ReadAction or synchronicity.
   *    *     Move execution part out of the code executed under ReadAction, or make your execution asynchronous - execute on
   *    *     {@link Task.Backgroundable other thread} and invoke a callback.</li>
   *     <li>Non-synchronous (you don't need to return something) - execute on other thread. E.g. using {@link Task.Backgroundable}</li>
   * </ul>
   * </li>
   *
   * <li>You are on EDT:
   * <ul>
   *
   * <li>Outside of {@link com.intellij.openapi.application.WriteAction WriteAction}:
   *   <ul>
   *     <li>Synchronous (you need to return execution result or derived information to the caller) - execute under
   *       {@link ProgressManager#runProcessWithProgressSynchronously(java.lang.Runnable, java.lang.String, boolean, com.intellij.openapi.project.Project) modal progress}.</li>
   *     <li>Non-synchronous (you don't need to return something) - execute on the pooled thread. E.g. using {@link Task.Backgroundable}</li>
   *   </ul>
   * </li>
   *
   * <li>Under {@link com.intellij.openapi.application.WriteAction WriteAction}
   *   <ul>
   *     <li>Synchronous (you need to return execution result or derived information to the caller) - get rid the WriteAction or synchronicity.
   *       Move execution part out of the code executed under WriteAction, or make your execution asynchronous - execute on
   *      {@link Task.Backgroundable other thread} and invoke a callback.</li>
   *     <li>Non-synchronous (you don't need to return something) - execute on the pooled thread. E.g. using {@link Task.Backgroundable}</li>
   *   </ul>
   * </li>
   * </ul></li></ul>
   *
   * @apiNote works only in internal mode with UI. Reports once per running session per stacktrace per cause.
   */
  public static void checkEdtAndReadAction(@NotNull ProcessHandler processHandler) {
    Application application = ApplicationManager.getApplication();
    if (application == null || !application.isInternal() || application.isHeadlessEnvironment()) {
      return;
    }
    String message = null;
    if (application.isDispatchThread()) {
      message = "Synchronous execution on EDT: ";
    }
    else if (application.isReadAccessAllowed()) {
      message = "Synchronous execution under ReadAction: ";
    }
    if (message != null && REPORTED_EXECUTIONS.add(ExceptionUtil.currentStackTrace())) {
      LOG.error(message + processHandler);
    }
  }

  private static void deleteTempFiles(Set<? extends File> tempFiles) {
    if (tempFiles != null) {
      try {
        for (File file : tempFiles) {
          FileUtil.delete(file);
        }
      }
      catch (Throwable t) {
        LOG.error("failed to delete temp. files", t);
      }
    }
  }

  /** @deprecated use {@link #OSProcessHandler(Process, String)} or any other constructor (to be removed in IDEA 2019) */
  @Deprecated
  public OSProcessHandler(@NotNull Process process) {
    this(process, null);
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public OSProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine) {
    this(process, commandLine, EncodingManager.getInstance().getDefaultCharset());
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public OSProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
    setHasPty(isPtyProcess(process));
  }

  private static boolean isPtyProcess(Process process) {
    Class c = process.getClass();
    while (c != null) {
      if ("com.pty4j.unix.UnixPtyProcess".equals(c.getName()) || "com.pty4j.windows.WinPtyProcess".equals(c.getName())) {
        return true;
      }
      c = c.getSuperclass();
    }
    return false;
  }

  @Override
  protected void onOSProcessTerminated(int exitCode) {
    super.onOSProcessTerminated(exitCode);
    deleteTempFiles(myFilesToDelete);
  }

  @Override
  protected boolean processHasSeparateErrorStream() {
    return myHasErrorStream;
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
      executeTask(() -> killProcessTreeSync(process));
    }
  }

  private void killProcessTreeSync(@NotNull Process process) {
    LOG.debug("killing process tree");
    final boolean destroyed = OSProcessManager.getInstance().killProcessTree(process);
    if (!destroyed) {
      if (!process.isAlive()) {
        LOG.warn("Process has been already terminated: " + myCommandLine);
      }
      else {
        LOG.warn("Cannot kill process tree. Trying to destroy process using Java API. Cmdline:\n" + myCommandLine);
        process.destroy();
      }
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

  @NotNull
  @Override
  protected BaseOutputReader.Options readerOptions() {
    return myHasPty ? BaseOutputReader.Options.BLOCKING : super.readerOptions();  // blocking read in case of PTY-based process
  }

  /**
   * Registers a file to delete after the given command line finishes.
   * In order to have an effect, the command line has to be executed with {@link #OSProcessHandler(GeneralCommandLine)}.
   */
  public static void deleteFileOnTermination(@NotNull GeneralCommandLine commandLine, @NotNull File fileToDelete) {
    Set<File> set = commandLine.getUserData(DELETE_FILES_ON_TERMINATION);
    if (set == null) {
      commandLine.putUserData(DELETE_FILES_ON_TERMINATION, set = new THashSet<>());
    }
    set.add(fileToDelete);
  }
}