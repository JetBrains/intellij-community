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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class ProcessListUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.impl.ProcessListUtil");

  @NotNull
  public static ProcessInfo[] getProcessList() {
    List<ProcessInfo> result = doGetProcessList();
    return result.isEmpty() ? ProcessInfo.EMPTY_ARRAY : result.toArray(new ProcessInfo[result.size()]);
  }

  @NotNull
  private static List<ProcessInfo> doGetProcessList() {
    List<ProcessInfo> result;
    if (SystemInfo.isWindows) {

      result = getProcessList_WindowsWMIC();
      if (result != null) return result;

      LOG.info("Cannot get process list via wmic, fallback to tasklist");
      result = getProcessList_WindowsTaskList();
      if (result != null) return result;

      LOG.error("Cannot get process list via wmic and tasklist");
    }
    else if (SystemInfo.isUnix) {
      result = getProcessList_Unix();
      if (result != null) return result;

      LOG.error("Cannot get process list");
    }
    else {
      LOG.error("Unexpected platform. Unable to list processes.");
    }
    return Collections.emptyList();
  }

  @Nullable
  private static List<ProcessInfo> parseCommandOutput(@NotNull List<String> command,
                                                      @NotNull NullableFunction<String, List<ProcessInfo>> parser) {
    String output;
    try {
      output = ExecUtil.execAndGetOutput(new GeneralCommandLine(command)).getStdout();
    }
    catch (ExecutionException ignore) {
      return null;
    }
    return parser.fun(output);
  }

  @Nullable
  private static List<ProcessInfo> getProcessList_Unix() {
    return parseCommandOutput(Arrays.asList("/bin/ps", "-a", "-x", "-o", "pid,state,user,command"),
                              new NullableFunction<String, List<ProcessInfo>>() {
                                @Nullable
                                @Override
                                public List<ProcessInfo> fun(String output) {
                                  return parseUnixOutput(output);
                                }
                              });
  }

  @Nullable
  static List<ProcessInfo> parseUnixOutput(@NotNull String output) {
    List<ProcessInfo> result = ContainerUtil.newArrayList();
    String[] lines = StringUtil.splitByLinesDontTrim(output);
    if (lines.length == 0) return null;

    String header = lines[0];
    int pidStart = header.indexOf("PID");
    if (pidStart == -1) return null;

    int statStart = header.indexOf("S", pidStart);
    if (statStart == -1) return null;

    int userStart = header.indexOf("USER", statStart);
    if (userStart == -1) return null;

    int commandStart = header.indexOf("COMMAND", userStart);
    if (commandStart == -1) return null;

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];

      int pid = StringUtil.parseInt(line.substring(0, statStart).trim(), -1);
      if (pid == -1) continue;

      String state = line.substring(statStart, userStart).trim();
      if (state.contains("Z")) continue; // zombie

      String user = line.substring(userStart, commandStart).trim();
      String commandLine = line.substring(commandStart).trim();

      String executablePath = determineExecutable(commandLine);
      if (executablePath == null) continue;

      String args = commandLine.substring(executablePath.length()).trim();
      
      String name = PathUtil.getFileName(StringUtil.trimTrailing(executablePath, '/'));
      result.add(new ProcessInfo(pid, commandLine, name, args, user, state));
    }

    return result;
  }

  @Nullable
  private static String determineExecutable(String commandLine) {
    // Since there is no way on Linux to get the path to the executable, we have to heuristically determine it
    // by finding the longest existing file path from the beginning of the command line.
    // There is a possibility to find the wrong path in ambiguous cases like the following:
    // * "/path/to/executable with spaces" - file name with spaces
    // * "/path/to/executable" "with spaces" - file name + arguments
    // Though probability of such a situation is negligible 

    String executablePath = commandLine;

    found:
    while (!new File(executablePath).exists()) {
      int separator = executablePath.lastIndexOf("/");
      if (separator == -1) return null;

      String parentPath = executablePath.substring(0, separator);

      if (new File(parentPath).exists()) {
        String name = executablePath.substring(separator + 1);
        int space = name.lastIndexOf(" ");

        while (true) {
          String namePart = name.substring(0, space == -1 ? name.length() : space);
          if (new File(parentPath, namePart).exists()) {
            executablePath = parentPath + "/" + namePart;
            break found;
          }

          if (space == -1) break;
          space = name.lastIndexOf(" ", space - 1);
        }
      }
      separator = parentPath.lastIndexOf(" ");
      if (separator == -1) return null;
      executablePath = parentPath.substring(0, separator);
    }

    assert commandLine.startsWith(executablePath) : "Executable incorrectly found: " + executablePath + " in: " + commandLine;
    return executablePath;
  }

  @Nullable
  static List<ProcessInfo> getProcessList_WindowsWMIC() {
    return parseCommandOutput(Arrays.asList("wmic.exe", "path", "win32_process", "get", "Caption,Processid,Commandline"),
                              new NullableFunction<String, List<ProcessInfo>>() {
                                @Nullable
                                @Override
                                public List<ProcessInfo> fun(String output) {
                                  return parseWMICOutput(output);
                                }
                              });
  }

  @Nullable
  static List<ProcessInfo> parseWMICOutput(@NotNull String output) {
    List<ProcessInfo> result = ContainerUtil.newArrayList();
    String[] lines = StringUtil.splitByLinesDontTrim(output);
    if (lines.length == 0) return null;

    String header = lines[0];
    int commandLineStart = header.indexOf("CommandLine");
    if (commandLineStart == -1) return null;

    int pidStart = header.indexOf("ProcessId");
    if (pidStart == -1) return null;

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];

      int pid = StringUtil.parseInt(line.substring(pidStart, line.length()).trim(), -1);
      if (pid == -1 || pid == 0) continue;

      String name = line.substring(0, commandLineStart).trim();
      if (name.isEmpty()) continue;

      String commandLine = line.substring(commandLineStart, pidStart).trim();
      String args = "";

      if (commandLine.isEmpty()) {
        commandLine = name;
      }
      else {
        int nameIndex = StringUtil.indexOfIgnoreCase(commandLine, name, 0);
        if (nameIndex != -1) {
          args = commandLine.substring(nameIndex + name.length()).trim();
        }
      }

      result.add(new ProcessInfo(pid, commandLine, name, args, null, null));
    }
    return result;
  }

  @Nullable
  static List<ProcessInfo> getProcessList_WindowsTaskList() {
    return parseCommandOutput(Arrays.asList("tasklist.exe", "/fo", "csv", "/nh", "/v"),
                              new NullableFunction<String, List<ProcessInfo>>() {
                                @Nullable
                                @Override
                                public List<ProcessInfo> fun(String output) {
                                  return parseListTasksOutput(output);
                                }
                              });
  }

  @Nullable
  static List<ProcessInfo> parseListTasksOutput(@NotNull String output) {
    List<ProcessInfo> result = ContainerUtil.newArrayList();

    CSVReader reader = new CSVReader(new StringReader(output));
    try {
      String[] next;
      while ((next = reader.readNext()) != null) {
        if (next.length < 2) return null;

        int pid = StringUtil.parseInt(next[1], -1);
        if (pid == -1) continue;

        String name = next[0];
        if (name.isEmpty()) continue;

        result.add(new ProcessInfo(pid, name, name, "", null, null));
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

    return result;
  }
}