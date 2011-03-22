/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.process.impl;

import com.intellij.execution.process.OSProcessManager;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jvnet.winp.WinProcess;
import org.jvnet.winp.WinpException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class OSProcessManagerImpl extends OSProcessManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.impl.OSProcessManagerImpl");

  @Override
  public boolean killProcessTree(@NotNull Process process) {
    if (SystemInfo.isWindows) {
      try {
        new WinProcess(process).killRecursively();
        return true;
      }
      catch (Throwable e) {
        LOG.info("Cannot kill process tree");
        LOG.info(e);
      }
    }
    else if (SystemInfo.isUnix) {
      return UnixProcessManager.sendSigKillToProcessTree(process);
    }
    return false;
  }

  @Override
  public List<String> getCommandLinesOfRunningProcesses() {
    try {
      if (SystemInfo.isWindows) {
        List<String> commandLines = new ArrayList<String>();
        Iterable<WinProcess> processes = WinProcess.all();
        for (WinProcess process : processes) {
          try {
            commandLines.add(process.getCommandLine());
          }
          catch (WinpException ignored) {
          }
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
