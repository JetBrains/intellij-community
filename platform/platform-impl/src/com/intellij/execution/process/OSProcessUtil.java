// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.process.impl.ProcessListUtil;
import com.intellij.openapi.util.SystemInfo;
import com.pty4j.windows.WinPtyProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jvnet.winp.WinProcess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OSProcessUtil {
  private static String ourPid;

  @NotNull
  public static ProcessInfo[] getProcessList() {
    return ProcessListUtil.getProcessList();
  }

  public static boolean killProcessTree(@NotNull Process process) {
    return ProcessUtil.killProcessTree(process);
  }

  public static void killProcess(@NotNull Process process) {
    killProcess(getProcessID(process));
  }

  public static void killProcess(int pid) {
    ProcessUtil.killProcess(pid);
  }

  public static int getProcessID(@NotNull Process process) {
    return ProcessUtil.getProcessID(process);
  }

  public static int getProcessID(@NotNull Process process, Boolean disableWinp) {
    return ProcessUtil.getProcessID(process, disableWinp);
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

  public static int getCurrentProcessId() {
    int pid;

    if (SystemInfo.isWindows) {
      pid = WinProcessManager.getCurrentProcessId();
    }
    else {
      pid = UnixProcessManager.getCurrentProcessId();
    }

    return pid;
  }

  public static String getApplicationPid() {
    if (ourPid == null) {
      ourPid = String.valueOf(getCurrentProcessId());
    }
    return ourPid;
  }

  /** @deprecated trivial, use {@link #getProcessList()} directly */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static List<String> getCommandLinesOfRunningProcesses() {
    List<String> result = new ArrayList<>();
    for (ProcessInfo each : getProcessList()) {
      result.add(each.getCommandLine());
    }
    return Collections.unmodifiableList(result);
  }
}