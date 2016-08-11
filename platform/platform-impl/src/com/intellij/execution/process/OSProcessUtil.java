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

/******************************************************************************
 * Copyright (C) 2013  Fabio Zadrozny
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Fabio Zadrozny <fabiofz@gmail.com> - initial API and implementation
 ******************************************************************************/
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
    if (SystemInfo.isWindows) {
      try {
        if (Registry.is("disable.winp")) {
          WinProcessManager.kill(process, false);
        }
        else {
          createWinProcess(process).kill();
        }
      }
      catch (Throwable e) {
        LOG.info("Cannot kill process", e);
      }
    }
    else if (SystemInfo.isUnix) {
      UnixProcessManager.sendSignal(UnixProcessManager.getProcessPid(process), UnixProcessManager.SIGKILL);
    }
  }

  public static int getProcessID(@NotNull Process process) {
    if (SystemInfo.isWindows) {
      try {
        if (Registry.is("disable.winp")) {
          return WinProcessManager.getProcessPid(process);
        }
        else {
          return createWinProcess(process).getPid();
        }
      }
      catch (Throwable e) {
        LOG.info("Cannot get process id", e);
        return -1;
      }
    }
    else if (SystemInfo.isUnix) {
      return UnixProcessManager.getProcessPid(process);
    }
    throw new IllegalStateException("Unknown OS: "  + SystemInfo.OS_NAME);
  }

  @SuppressWarnings("deprecation")
  @NotNull
  private static WinProcess createWinProcess(@NotNull Process process) {
    if (process instanceof RunnerWinProcess) process = ((RunnerWinProcess)process).getOriginalProcess();
    return new WinProcess(process);
  }
  
  @Nullable
  public static List<String> getCommandLinesOfRunningProcesses() {
    List<String> result = new ArrayList<>();
    for (ProcessInfo each : getProcessList()) {
      result.add(each.getCommandLine());
    }
    return Collections.unmodifiableList(result); 
  }
}
