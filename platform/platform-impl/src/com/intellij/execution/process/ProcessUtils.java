/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.winp.WinProcess;
import org.jvnet.winp.WinpException;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProcessUtils {
  private static final Logger LOG = Logger.getInstance(ProcessUtils.class);

  /**
   * Passes the commands directly to Runtime.exec (with the passed envp)
   */
  @NotNull
  public static Process createProcess(String[] cmdarray, String[] envp, File workingDir) throws IOException {
    return Runtime.getRuntime().exec(getWithoutEmptyParams(cmdarray), getWithoutEmptyParams(envp), workingDir);
  }

  /**
   * @return a new array without any null/empty elements originally contained in the array.
   */
  @Nullable
  private static String[] getWithoutEmptyParams(@Nullable String[] cmdarray) {
    if (cmdarray == null) {
      return null;
    }
    ArrayList<String> list = new ArrayList<String>();
    for (String string : cmdarray) {
      if (string != null && string.length() > 0) {
        list.add(string);
      }
    }
    return ArrayUtil.toStringArray(list);
  }

  @NotNull
  public static IProcessList getProcessList(@NotNull String helpersRoot) {
    if (SystemInfo.isWindows) {
      return new ProcessListWin32(helpersRoot);
    }
    if (SystemInfo.isLinux) {
      return new ProcessListLinux();
    }
    if (SystemInfo.isMac) {
      return new ProcessListMac();
    }

    LOG.error("Unexpected platform. Unable to list processes.");
    return new IProcessList() {

      @Override
      public ProcessInfo[] getProcessList() {
        return new ProcessInfo[0];
      }
    };
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file, @Nullable String encoding) throws IOException {
    InputStream stream = new FileInputStream(file);
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    Reader reader = encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
    try {
      return loadText(reader);
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  public static char[] loadText(@NotNull Reader reader) throws IOException {
    //fill a buffer with the contents
    int BUFFER_SIZE = 2 * 1024;

    char[] readBuffer = new char[BUFFER_SIZE];
    int n = reader.read(readBuffer);

    int DEFAULT_FILE_SIZE = 8 * BUFFER_SIZE;

    StringBuilder builder = new StringBuilder(DEFAULT_FILE_SIZE);

    while (n > 0) {
      builder.append(readBuffer, 0, n);
      n = reader.read(readBuffer);
    }

    return builder.toString().toCharArray();
  }

  public static boolean killProcessTree(@NotNull Process process) {
    if (SystemInfo.isWindows) {
      try {
        WinProcess winProcess = createWinProcess(process);
        winProcess.killRecursively();
        return true;
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
        WinProcess winProcess = createWinProcess(process);
        winProcess.kill();
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
        return createWinProcess(process).getPid();
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
    try {
      if (SystemInfo.isWindows) {
        List<String> commandLines = new ArrayList<String>();
        for (WinProcess process : WinProcess.all()) {
          try {
            commandLines.add(process.getCommandLine());
          }
          catch (WinpException ignored) { }
        }
        return commandLines;
      }
      else {
        String[] cmd = UnixProcessManager.getPSCmd(true);
        Process process = Runtime.getRuntime().exec(cmd);
        List<String> outputLines = readLines(process.getInputStream(), false);
        List<String> errorLines = readLines(process.getErrorStream(), false);
        if (!errorLines.isEmpty()) {
          throw new IOException(Arrays.toString(cmd) + " failed: " + StringUtil.join(errorLines, "\n"));
        }

        //trim 'ps' output header
        return outputLines.subList(1, outputLines.size());
      }
    }
    catch (Throwable e) {
      LOG.info("Cannot collect command lines");
      LOG.info(e);
      return null;
    }
  }

  @NotNull
  private static List<String> readLines(@NotNull InputStream inputStream, boolean includeEmpty) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    try {
      List<String> lines = new ArrayList<String>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (includeEmpty || !line.isEmpty()) {
          lines.add(line);
        }
      }
      return lines;
    }
    finally {
      reader.close();
    }
  }
}
