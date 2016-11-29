/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.configurations;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A flavor of GeneralCommandLine to start processes with Pseudo-Terminal (PTY).
 *
 * Warning: PtyCommandLine works with ProcessHandler only in blocking read mode.
 * Please make sure that you use appropriate ProcessHandler implementation.
 *
 * Note: this works only on Unix, on Windows regular processes are used instead.
 */
public class PtyCommandLine extends GeneralCommandLine {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.PtyCommandLine");
  private static final String RUN_PROCESSES_WITH_PTY = "run.processes.with.pty";

  public static boolean isEnabled() {
    return Registry.is(RUN_PROCESSES_WITH_PTY);
  }

  private boolean myUseCygwinLaunch;
  private boolean myConsoleMode = true;

  public PtyCommandLine() { }

  @NotNull
  @Override
  protected Process startProcess(@NotNull List<String> commands) throws IOException {
    try {
      return startProcessWithPty(commands, myConsoleMode);
    }
    catch (Throwable t) {
      File logFile = getPtyLogFile();
      if (logFile != null && logFile.exists()) {
        String logContent;
        try {
          logContent = FileUtil.loadFile(logFile);
        }
        catch (Exception e) {
          logContent = "Unable to retrieve log: " + e.getMessage();
        }

        LOG.error("Couldn't run process with PTY", t, logContent);
      }
      else {
        LOG.error("Couldn't run process with PTY", t);
      }
    }

    return super.startProcess(commands);
  }

  public void setUseCygwinLaunch(boolean useCygwinLaunch) {
    myUseCygwinLaunch = useCygwinLaunch;
  }

  public void setConsoleMode(boolean consoleMode) {
    myConsoleMode = consoleMode;
  }

  private static File getPtyLogFile() {
    Application app = ApplicationManager.getApplication();
    return app != null && app.isEAP() ? new File(PathManager.getLogPath(), "pty.log") : null;
  }

  @NotNull
  public Process startProcessWithPty(@NotNull List<String> commands, boolean console) throws IOException {
    Map<String, String> env = new HashMap<>();
    setupEnvironment(env);

    if (isRedirectErrorStream()) {
      LOG.error("Launching process with PTY and redirected error stream is unsupported yet");
    }

    String[] command = ArrayUtil.toStringArray(commands);
    File workDirectory = getWorkDirectory();
    String directory = workDirectory != null ? workDirectory.getPath() : null;
    boolean cygwin = myUseCygwinLaunch && SystemInfo.isWindows;
    return PtyProcess.exec(command, env, directory, console, cygwin, getPtyLogFile());
  }
}