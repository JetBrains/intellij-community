// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import com.pty4j.PtyProcessBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A flavor of GeneralCommandLine to start processes with Pseudo-Terminal (PTY).
 *
 * Warning: PtyCommandLine works with ProcessHandler only in blocking read mode.
 * Please make sure that you use appropriate ProcessHandler implementation.
 *
 * Works for Linux, macOS, and Windows.
 * On Windows, PTY is emulated by creating an invisible console window (see Pty4j and WinPty implementation).
 */
public class PtyCommandLine extends GeneralCommandLine {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.PtyCommandLine");
  private static final String RUN_PROCESSES_WITH_PTY = "run.processes.with.pty";

  private static final String UNIX_PTY_INIT = "unix.pty.init";
  private static final String UNIX_PTY_COLUMNS = "unix.pty.cols";
  private static final String UNIX_PTY_ROWS = "unix.pty.rows";

  private static final String WIN_PTY_COLUMNS = "win.pty.cols";
  private static final String WIN_PTY_ROWS = "win.pty.rows";

  public static boolean isEnabled() {
    return Registry.is(RUN_PROCESSES_WITH_PTY);
  }

  private boolean myUseCygwinLaunch;
  private boolean myConsoleMode = true;
  private int myInitialColumns = -1;
  private int myInitialRows = -1;

  public PtyCommandLine() { }

  public void setUseCygwinLaunch(boolean useCygwinLaunch) {
    myUseCygwinLaunch = useCygwinLaunch;
  }

  public void setConsoleMode(boolean consoleMode) {
    myConsoleMode = consoleMode;
  }

  public void setInitialColumns(int initialColumns) {
    myInitialColumns = initialColumns;
  }

  public void setInitialRows(int initialRows) {
    myInitialRows = initialRows;
  }

  @NotNull
  @Override
  protected Process startProcess(@NotNull List<String> commands) throws IOException {
    try {
      return startProcessWithPty(commands);
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

        LOG.debug("Couldn't run process with PTY", t, logContent);
      }
      else {
        LOG.debug("Couldn't run process with PTY", t);
      }
    }

    return super.startProcess(commands);
  }

  private static File getPtyLogFile() {
    Application app = ApplicationManager.getApplication();
    return app != null && app.isEAP() ? new File(PathManager.getLogPath(), "pty.log") : null;
  }

  @NotNull
  public Process startProcessWithPty(@NotNull List<String> commands) throws IOException {
    List<Pair<String, String>> backup = new ArrayList<>();
    try {
      if (SystemInfo.isUnix && (myInitialColumns > 0 || myInitialRows > 0)) {
        setSystemProperty(UNIX_PTY_INIT, Boolean.toString(true), backup);
        if (myInitialColumns > 0) {
          setSystemProperty(UNIX_PTY_COLUMNS, Integer.toString(myInitialColumns), backup);
        }
        if (myInitialRows > 0) {
          setSystemProperty(UNIX_PTY_ROWS, Integer.toString(myInitialRows), backup);
        }
      }
      else if (SystemInfo.isWindows) {
        if (myInitialColumns > 0) {
          setSystemProperty(WIN_PTY_COLUMNS, Integer.toString(myInitialColumns), backup);
        }
        if (myInitialRows > 0) {
          setSystemProperty(WIN_PTY_ROWS, Integer.toString(myInitialRows), backup);
        }
      }
      return doStartProcessWithPty(commands);
    }
    finally {
      for (Pair<String, String> pair : backup) {
        setSystemProperty(pair.first, pair.second, null);
      }
    }
  }

  private static void setSystemProperty(@NotNull String propertyName,
                                        @Nullable String newPropertyValue,
                                        @Nullable List<Pair<String, String>> backup) {
    if (backup != null) {
      String oldValue = System.getProperty(propertyName);
      backup.add(Pair.create(propertyName, oldValue));
    }
    if (newPropertyValue != null) {
      System.setProperty(propertyName, newPropertyValue);
    }
    else {
      System.clearProperty(propertyName);
    }
  }

  @NotNull
  private Process doStartProcessWithPty(@NotNull List<String> commands) throws IOException {
    Map<String, String> env = new HashMap<>();
    setupEnvironment(env);

    String[] command = ArrayUtil.toStringArray(commands);
    File workDirectory = getWorkDirectory();
    String directory = workDirectory != null ? workDirectory.getPath() : null;
    boolean cygwin = myUseCygwinLaunch && SystemInfo.isWindows;
    PtyProcessBuilder builder = new PtyProcessBuilder(command)
      .setEnvironment(env)
      .setDirectory(directory)
      .setConsole(myConsoleMode)
      .setCygwin(cygwin)
      .setLogFile(getPtyLogFile())
      .setRedirectErrorStream(isRedirectErrorStream());
    return builder.start();
  }
}