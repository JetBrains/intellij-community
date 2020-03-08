// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtilRt;
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
  private static final Logger LOG = Logger.getInstance(PtyCommandLine.class);
  private static final String RUN_PROCESSES_WITH_PTY = "run.processes.with.pty";

  private static final String UNIX_PTY_INIT = "unix.pty.init";
  private static final String UNIX_PTY_COLUMNS = "unix.pty.cols";
  private static final String UNIX_PTY_ROWS = "unix.pty.rows";

  private static final String WIN_PTY_COLUMNS = "win.pty.cols";
  private static final String WIN_PTY_ROWS = "win.pty.rows";

  public static final int MAX_COLUMNS = 2500;

  public static boolean isEnabled() {
    return Registry.is(RUN_PROCESSES_WITH_PTY);
  }

  private boolean myUseCygwinLaunch;
  private boolean myConsoleMode = true;
  private int myInitialColumns = -1;
  private int myInitialRows = -1;
  private boolean myWindowsAnsiColorEnabled = !Boolean.getBoolean("pty4j.win.disable.ansi.in.console.mode");

  public PtyCommandLine() { }

  /**
   * @deprecated use {@link #withUseCygwinLaunch(boolean)}
   */
  @Deprecated
  public void setUseCygwinLaunch(boolean useCygwinLaunch) {
    withUseCygwinLaunch(useCygwinLaunch);
  }

  /**
   * @deprecated use {@link #withConsoleMode(boolean)}
   */
  @Deprecated
  public void setConsoleMode(boolean consoleMode) {
    withConsoleMode(consoleMode);
  }

  /**
   * @deprecated use {@link #withInitialColumns(int)}
   */
  @Deprecated
  public void setInitialColumns(int initialColumns) {
    withInitialColumns(initialColumns);
  }

  /**
   * @deprecated use {@link #withInitialRows(int)}
   */
  @Deprecated
  public void setInitialRows(int initialRows) {
    withInitialRows(initialRows);
  }

  public PtyCommandLine withUseCygwinLaunch(boolean useCygwinLaunch) {
    myUseCygwinLaunch = useCygwinLaunch;
    return this;
  }

  public PtyCommandLine withConsoleMode(boolean consoleMode) {
    myConsoleMode = consoleMode;
    return this;
  }

  public boolean isConsoleMode() {
    return myConsoleMode;
  }

  public PtyCommandLine withInitialColumns(int initialColumns) {
    myInitialColumns = initialColumns;
    return this;
  }

  public PtyCommandLine withInitialRows(int initialRows) {
    myInitialRows = initialRows;
    return this;
  }

  public PtyCommandLine(@NotNull List<String> command) {
    super(command);
  }

  public PtyCommandLine(@NotNull GeneralCommandLine original) {
    super(original);
    if (original instanceof PtyCommandLine) {
      myUseCygwinLaunch = ((PtyCommandLine)original).myUseCygwinLaunch;
      myConsoleMode = ((PtyCommandLine)original).myConsoleMode;
      myInitialColumns = ((PtyCommandLine)original).myInitialColumns;
      myInitialRows = ((PtyCommandLine)original).myInitialRows;
    }
  }

  @NotNull
  PtyCommandLine withWindowsAnsiColorDisabled() {
    myWindowsAnsiColorEnabled = false;
    return this;
  }

  @NotNull
  @Override
  protected Process startProcess(@NotNull List<String> commands) throws IOException {
    if (getInputFile() == null) {
      try {
        return startProcessWithPty(commands);
      }
      catch (Throwable t) {
        String message = "Couldn't run process with PTY";
        if (LOG.isDebugEnabled()) {
          String logFileContent = loadLogFile();
          if (logFileContent != null) {
            LOG.debug(message, t, logFileContent);
          }
          else {
            LOG.warn(message, t);
          }
        }
        else {
          LOG.warn(message, t);
        }
      }
    }
    return super.startProcess(commands);
  }

  @Nullable
  private static String loadLogFile() {
    Application app = ApplicationManager.getApplication();
    File logFile = app != null && app.isEAP() ? new File(PathManager.getLogPath(), "pty.log") : null;
    if (logFile != null && logFile.exists()) {
      try {
        return FileUtil.loadFile(logFile);
      }
      catch (Exception e) {
        return "Unable to retrieve pty log: " + e.getMessage();
      }
    }
    return null;
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
                                        @Nullable List<? super Pair<String, String>> backup) {
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

    String[] command = ArrayUtilRt.toStringArray(commands);
    File workDirectory = getWorkDirectory();
    String directory = workDirectory != null ? workDirectory.getPath() : null;
    boolean cygwin = myUseCygwinLaunch && SystemInfo.isWindows;
    Application app = ApplicationManager.getApplication();
    PtyProcessBuilder builder = new PtyProcessBuilder(command)
      .setEnvironment(env)
      .setDirectory(directory)
      .setConsole(myConsoleMode)
      .setCygwin(cygwin)
      .setLogFile(app != null && app.isEAP() ? new File(PathManager.getLogPath(), "pty.log") : null)
      .setRedirectErrorStream(isRedirectErrorStream())
      .setWindowsAnsiColorEnabled(myWindowsAnsiColorEnabled);
    return builder.start();
  }
}