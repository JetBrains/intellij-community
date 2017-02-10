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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

/**
 * @author Alexey.Ushakov
 */
class WinProcessManager {
  private static final Logger LOG = Logger.getInstance(WinProcessManager.class);

  private WinProcessManager() {}

  /**
   * Returns {@code pid} for Windows process
   * @param process Windows process
   * @return pid of the {@code process}
   */
  public static int getProcessPid(Process process) {
    if (process.getClass().getName().equals("java.lang.Win32Process") ||
        process.getClass().getName().equals("java.lang.ProcessImpl")) {
      try {
        long handle = ReflectionUtil.getField(process.getClass(), process, long.class, "handle");

        Kernel32 kernel = Kernel32.INSTANCE;
        WinNT.HANDLE winHandle = new WinNT.HANDLE();
        winHandle.setPointer(Pointer.createConstant(handle));
        return kernel.GetProcessId(winHandle);
      } catch (Throwable e) {
        throw new IllegalStateException(e);
      }
    } else {
      throw new IllegalStateException("Unknown Process implementation");
    }
  }

  /**
   * Force kill a process (tree)
   * @param process Windows process
   * @param tree true to also kill all subprocesses
   */
  public static boolean kill(Process process, boolean tree) {
    return kill(-1, process, tree);
  }

  public static boolean kill(int pid, boolean tree) {
    return kill(pid, null, tree);
  }

  private static boolean kill(int pid, Process process, boolean tree) {
    LOG.assertTrue(pid > 0 || process != null);
    try {
      if (process != null) {
        pid = getProcessPid(process);
      }
      String[] cmdArray = {"taskkill", "/f", "/pid", String.valueOf(pid), tree ? "/t" : ""};
      if (LOG.isDebugEnabled()) {
        LOG.debug(StringUtil.join(cmdArray, " "));
      }
      Process p = new ProcessBuilder(cmdArray).redirectErrorStream(true).start();
      String output = FileUtil.loadTextAndClose(p.getInputStream());
      int res = p.waitFor();

      if (res != 0 && (process == null || process.isAlive())) {
        LOG.warn(StringUtil.join(cmdArray, " ") + " failed: " + output);
        return false;
      }
      else if (LOG.isDebugEnabled()) {
        LOG.debug(output);
      }

      return true;
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return false;
  }
}
