// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.process;

import com.intellij.execution.process.impl.ProcessListUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.pty4j.windows.WinPtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jvnet.winp.WinProcess;

public class OSProcessUtil {
  private static final Logger LOG = Logger.getInstance(OSProcessUtil.class);

  @NotNull
  public static ProcessInfo[] getProcessList() {
    return ProcessListUtil.getProcessList();
  }

  public static boolean killProcessTree(@NotNull Process process) {
    if (SystemInfo.isWindows) {
      try {
        if (process instanceof WinPtyProcess) {
          boolean res = WinProcessManager.kill(((WinPtyProcess)process).getChildProcessId(), true);
          process.destroy();
          return res;
        }
        if (Registry.is("disable.winp")) {
          return WinProcessManager.kill(process, true);
        }
        else {
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
        if (Registry.is("disable.winp")) {
          WinProcessManager.kill(pid, false);
        }
        else {
          createWinProcess(pid).kill();
        }
      }
      catch (Throwable e) {
        LOG.info("Cannot kill process", e);
      }
    }
    else if (SystemInfo.isUnix) {
      UnixProcessManager.sendSignal(pid, UnixProcessManager.SIGKILL);
    }
  }

  public static int getProcessID(@NotNull Process process) {
    if (SystemInfo.isWindows) {
      try {
        if (process instanceof WinPtyProcess) {
          return ((WinPtyProcess)process).getChildProcessId();
        }
        if (Registry.is("disable.winp")) {
          return WinProcessManager.getProcessId(process);
        }
        else {
          return createWinProcess(process).getPid();
        }
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
  private static WinProcess createWinProcess(@NotNull Process process) {
    if (process instanceof RunnerWinProcess) process = ((RunnerWinProcess)process).getOriginalProcess();
    return new WinProcess(process);
  }

  @NotNull
  private static WinProcess createWinProcess(int pid) {
    return new WinProcess(pid);
  }
}