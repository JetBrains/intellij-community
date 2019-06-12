// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Utility class to start a process with a runner mediator (runnerw.exe) injected into a command line,
 * which adds a capability to terminate process tree gracefully by sending it a Ctrl+Break through stdin.
 *
 * @author traff
 */
public class RunnerMediator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.RunnerMediator");

  private static final char IAC = (char)5;
  private static final char BRK = (char)3;
  private static final char C = (char)5;
  private static final String RUNNERW = "runnerw.exe";
  private static final String RUNNERW_64 = "runnerw64.exe";
  private static final String IDEA_RUNNERW = "IDEA_RUNNERW";

  /**
   * Creates default runner mediator
   * @return
   */
  public static RunnerMediator getInstance() {
    return new RunnerMediator();
  }

  /**
   * Sends sequence of two chars(codes 5 and {@code event}) to a process output stream
   */
  private static void sendCtrlEventThroughStream(@NotNull final Process process, final char event) {
    OutputStream os = process.getOutputStream();
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    PrintWriter pw = new PrintWriter(os);
    pw.print(IAC);
    pw.print(event);
    pw.flush();
  }

  /**
   * In case of windows creates process with runner mediator(runnerw.exe) injected to command line string, which adds a capability
   * to terminate process tree gracefully with ctrl+break.
   *
   * Returns appropriate process handle, which in case of Unix is able to terminate whole process tree by sending sig_kill
   *
   */
  public ProcessHandler createProcess(@NotNull final GeneralCommandLine commandLine) throws ExecutionException {
    return createProcess(commandLine, false);
  }

  public ProcessHandler createProcess(@NotNull final GeneralCommandLine commandLine, final boolean useSoftKill) throws ExecutionException {
    if (SystemInfo.isWindows) {
      injectRunnerCommand(commandLine, false);
    }

    return new CustomDestroyProcessHandler(commandLine, useSoftKill);
  }

  @Nullable
  private static String getRunnerPath() {
    if (!SystemInfo.isWindows) {
      throw new IllegalStateException("There is no need of runner under unix based OS");
    }

    String path = System.getenv(IDEA_RUNNERW);
    if (path != null) {
      if (new File(path).exists()) {
        return path;
      }
      LOG.warn("Cannot locate " + RUNNERW + " by " + IDEA_RUNNERW + "=" + path);
    }

    String[] names = Platform.is64Bit() ? new String[] {RUNNERW_64, RUNNERW} : new String[] {RUNNERW};
    for (String name : names) {
      File runnerw = PathManager.findBinFile(name);
      if (runnerw != null && runnerw.exists()) {
        return runnerw.getPath();
      }
    }

    LOG.warn("Cannot locate " + RUNNERW + " in " + PathManager.getBinPath());
    return null;
  }

  static boolean injectRunnerCommand(@NotNull GeneralCommandLine commandLine, boolean showConsole) {
    final String path = getRunnerPath();
    if (path != null) {
      commandLine.getParametersList().addAt(0, commandLine.getExePath());
      if (showConsole)
        commandLine.getParametersList().addAt(0, "/C");
      commandLine.setExePath(path);
      return true;
    }
    return false;
  }

  /**
   * Destroys process tree: in case of windows via imitating ctrl+break, in case of unix via sending sig_kill to every process in tree.
   * @param process to kill with all sub-processes.
   */
  public static boolean destroyProcess(@NotNull final Process process) {
    return destroyProcess(process, false);
  }

  /**
   * Destroys process tree: in case of windows via imitating ctrl+c, in case of unix via sending sig_int to every process in tree.
   * @param process to kill with all sub-processes.
   */
  static boolean destroyProcess(@NotNull final Process process, final boolean softKill) {
    try {
      if (SystemInfo.isWindows) {
        sendCtrlEventThroughStream(process, softKill ? C : BRK);
        return true;
      }
      else if (SystemInfo.isUnix) {
        if (softKill) {
          return UnixProcessManager.sendSigIntToProcessTree(process);
        }
        else {
          return UnixProcessManager.sendSigKillToProcessTree(process);
        }
      }
      else {
        return false;
      }
    }
    catch (Exception e) {
      LOG.error("Couldn't terminate the process", e);
      return false;
    }
  }

  public static class CustomDestroyProcessHandler extends ColoredProcessHandler {
    private final boolean mySoftKill;

    /** @deprecated use CustomDestroyProcessHandler(GeneralCommandLine commandLine) (to remove in IDEA 16) */
    @Deprecated
    public CustomDestroyProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
      super(process, commandLine.getCommandLineString());
      mySoftKill = false;
    }

    public CustomDestroyProcessHandler(@NotNull GeneralCommandLine commandLine, final boolean softKill) throws ExecutionException {
      super(commandLine);
      mySoftKill = softKill;
    }

    @Override
    protected boolean shouldDestroyProcessRecursively(){
      return true;
    }
    @Override
    protected void destroyProcessImpl() {
      if (!RunnerMediator.destroyProcess(getProcess(), mySoftKill)) {
        super.destroyProcessImpl();
      }
    }
  }
}
