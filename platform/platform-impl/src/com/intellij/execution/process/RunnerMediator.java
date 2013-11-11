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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * @author traff
 */
public class RunnerMediator {
  public static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.RunnerMediator");

  private static final char IAC = (char)5;
  private static final char BRK = (char)3;
  private static final char C = (char)5;
  private static final String STANDARD_RUNNERW = "runnerw.exe";

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
    PrintWriter pw = new PrintWriter(os);
    try {
      pw.print(IAC);
      pw.print(event);
      pw.flush();
    }
    finally {
      pw.close();
    }
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
      injectRunnerCommand(commandLine);
    }

    Process process = commandLine.createProcess();

    return new CustomDestroyProcessHandler(process, commandLine, useSoftKill);
  }

  @Nullable
  private static String getRunnerPath() {
    if (SystemInfo.isWindows) {
      final String path = System.getenv("IDEA_RUNNERW");
      if (path != null && new File(path).exists()) {
        return path;
      }
      File runnerw = new File(PathManager.getBinPath(), STANDARD_RUNNERW);
      if (runnerw.exists()) {
        return runnerw.getPath();
      }
      return null;
    }
    else {
      throw new IllegalStateException("There is no need of runner under unix based OS");
    }
  }

  private static void injectRunnerCommand(@NotNull GeneralCommandLine commandLine) {
    final String path = getRunnerPath();
    if (path != null) {
      commandLine.getParametersList().addAt(0, commandLine.getExePath());
      commandLine.setExePath(path);
    }
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
  private static boolean destroyProcess(@NotNull final Process process, final boolean softKill) {
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

    public CustomDestroyProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
      this(process, commandLine, false);
    }
    public CustomDestroyProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine, final boolean softKill) {
      super(process, commandLine.getCommandLineString());
      mySoftKill = softKill;
    }

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
