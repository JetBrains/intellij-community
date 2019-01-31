// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.process;

import com.intellij.execution.process.impl.ProcessListUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.pty4j.windows.WinPtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.winp.WinProcess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OSProcessUtil {
  private static final Logger LOG = Logger.getInstance(OSProcessUtil.class);
  private static String ourPid;

  @NotNull
  public static ProcessInfo[] getProcessList() {
    return ProcessListUtil.getProcessList();
  }

  public static boolean killProcessTree(@NotNull Process process) {
    if (SystemInfo.isWindows) {
      try {
        if (process instanceof WinPtyProcess) {
          int pid = ((WinPtyProcess) process).getChildProcessId();
          if (pid == -1) return true;
          boolean res = WinProcessManager.kill(pid, true);
          process.destroy();
          return res;
        }
        if (Registry.is("disable.winp")) {
          return WinProcessManager.kill(process, true);
        }
        else {
          if (!process.isAlive()) {
            logSkippedActionWithTerminatedProcess(process, "killProcessTree", null);
            return true;
          }
          createWinProcess(process).killRecursively();
          return true;
        }
      }
      catch (Throwable e) {
        LOG.info("Cannot kill process tree", e);
      }
    }
    else if (SystemInfo.isUnix) {
      return UnixProcessManager.sendSigKillToProcessTree(process);
    }
    return false;
  }

  public static void killProcess(@NotNull Process process) {
    killProcess(getProcessID(process));
  }

  public static void killProcess(int pid) {
    if (SystemInfo.isWindows) {
      try {
        if (!Registry.is("disable.winp")) {
          try {
            createWinProcess(pid).kill();
            return;
          }
          catch (Throwable e) {
            LOG.error("Failed to kill process with winp, fallback to default logic", e);
          }
        }
        WinProcessManager.kill(pid, false);
      }
      catch (Throwable e) {
        LOG.info("Cannot kill process", e);
      }
    }
    else if (SystemInfo.isUnix) {
      UnixProcessManager.sendSignal(pid, UnixProcessManager.SIGKILL);
    }
  }

  static void logSkippedActionWithTerminatedProcess(@NotNull Process process, @NotNull String actionName, @Nullable String commandLine) {
    Integer pid = null;
    try {
      pid = getProcessID(process);
    }
    catch (Throwable ignored) {
    }
    LOG.info("Cannot " + actionName + " already terminated process (pid: " + pid + ", command: " + commandLine + ")");
  }

  public static int getProcessID(@NotNull Process process) {
    if (SystemInfo.isWindows) {
      try {
        if (process instanceof WinPtyProcess) {
          return ((WinPtyProcess)process).getChildProcessId();
        }
        if (!Registry.is("disable.winp")) {
          try {
            return createWinProcess(process).getPid();
          }
          catch (Throwable e) {
            LOG.error("Failed to get PID with winp, fallback to default logic", e);
          }
        }
        return WinProcessManager.getProcessId(process);
      }
      catch (Throwable e) {
        throw new IllegalStateException("Cannot get PID from instance of " + process.getClass()
                                        + ", OS: " + SystemInfo.OS_NAME, e);
      }
    }
    else if (SystemInfo.isUnix) {
      return UnixProcessManager.getProcessId(process);
    }
    throw new IllegalStateException("Unknown OS: "  + SystemInfo.OS_NAME);
  }

  @SuppressWarnings("deprecation")
  @NotNull
  static WinProcess createWinProcess(@NotNull Process process) {
    if (process instanceof RunnerWinProcess) process = ((RunnerWinProcess)process).getOriginalProcess();
    if (process instanceof WinPtyProcess) {
      return new WinProcess(((WinPtyProcess)process).getPid());
    }
    return new WinProcess(process);
  }

  @NotNull
  private static WinProcess createWinProcess(int pid) {
    return new WinProcess(pid);
  }

  private static String getCurrentProcessId() {
    int pid;

    if (SystemInfo.isWindows) {
      pid = WinProcessManager.getCurrentProcessId();
    }
    else {
      pid = UnixProcessManager.getCurrentProcessId();
    }

    return String.valueOf(pid);
  }

  public static String getApplicationPid() {
    if (ourPid == null) {
      ourPid = getCurrentProcessId();
    }

    return ourPid;
  }

  /** @deprecated trivial; use {@link #getProcessList()} directly (to be removed in IDEA 2019) */
  @Deprecated
  public static List<String> getCommandLinesOfRunningProcesses() {
    List<String> result = new ArrayList<>();
    for (ProcessInfo each : getProcessList()) {
      result.add(each.getCommandLine());
    }
    return Collections.unmodifiableList(result);
  }
}