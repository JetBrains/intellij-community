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
package com.intellij.execution.process.impl;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

class ProcessListWin32 {
  @NotNull
  public static ProcessInfo[] getProcessList() {
    ProcessInfo[] result;

    result = getProcessListFromWMIC();
    if (result != null) return result;

    result = getProcessListFromTaskList();
    if (result != null) return result;

    return ProcessInfo.EMPTY_ARRAY;
  }

  @Nullable
  static ProcessInfo[] getProcessListFromWMIC() {
    String output;
    try {
      GeneralCommandLine cl = new GeneralCommandLine("wmic.exe", "path", "win32_process", "get", "Caption,Processid,Commandline");
      output = ExecUtil.execAndGetOutput(cl).getStdout();
    }
    catch (ExecutionException ignore) {
      return null;
    }
    return parseWMICOutput(output);
  }

  @Nullable
  static ProcessInfo[] parseWMICOutput(@NotNull String output) {
    List<ProcessInfo> result = ContainerUtil.newArrayList();
    String[] lines = StringUtil.splitByLinesDontTrim(output);
    if (lines.length < 1) return null;

    String header = lines[0];
    int commandLineI = header.indexOf("CommandLine");
    if (commandLineI == -1) return null;
    int processIdI = header.indexOf("ProcessId");
    if (processIdI == -1) return null;

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];

      int pid = StringUtil.parseInt(line.substring(processIdI, line.length()).trim(), -1);
      if (pid == -1 || pid == 0) continue;

      String name = line.substring(0, commandLineI).trim();
      if (name.isEmpty()) continue;

      String commandLine = line.substring(commandLineI, processIdI).trim();
      String args = "";

      int nameIndex = StringUtil.indexOfIgnoreCase(commandLine, name, 0);
      if (nameIndex != -1) {
        args = commandLine.substring(nameIndex + name.length()).trim();
      }

      result.add(new ProcessInfo(pid, name, args));
    }
    return result.isEmpty() ? ProcessInfo.EMPTY_ARRAY : result.toArray(new ProcessInfo[result.size()]);
  }

  @Nullable
  static ProcessInfo[] getProcessListFromTaskList() {
    String output;
    try {
      GeneralCommandLine cl = new GeneralCommandLine("tasklist.exe", "/fo", "csv", "/nh", "/v");
      output = ExecUtil.execAndGetOutput(cl).getStdout();
    }
    catch (ExecutionException ignore) {
      return null;
    }
    return parseListTasksOutput(output);
  }

  @Nullable
  static ProcessInfo[] parseListTasksOutput(@NotNull String output) {
    List<ProcessInfo> result = ContainerUtil.newArrayList();

    CSVReader reader = new CSVReader(new StringReader(output));
    try {
      String[] next;
      while ((next = reader.readNext()) != null) {
        if (next.length < 2) continue;
        
        int pid = StringUtil.parseInt(next[1], -1);
        if (pid == -1) continue;
        
        String name = next[0];
        if (name.isEmpty()) continue;
        
        result.add(new ProcessInfo(pid, name, ""));
      }
    }
    catch (IOException ignore) {
      return null;
    }
    finally {
      try {
        reader.close();
      }
      catch (IOException ignore) {
      }
    }

    return result.isEmpty() ? ProcessInfo.EMPTY_ARRAY : result.toArray(new ProcessInfo[result.size()]);
  }
}