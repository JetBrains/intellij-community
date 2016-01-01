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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;


class ProcessListLinux {
  @NotNull
  public static ProcessInfo[] getProcessList(boolean isMac) {
    String output;
    try {
      output = ExecUtil.execAndGetOutput(new GeneralCommandLine("/bin/ps", "-a", "-x", "-o", "pid,state,user,command")).getStdout();
    }
    catch (ExecutionException ignore) {
      return ProcessInfo.EMPTY_ARRAY;
    }
    return parseOutput(isMac, output);
  }

  @NotNull
  static ProcessInfo[] parseOutput(boolean isMac, @NotNull String output) {
    List<ProcessInfo> result = ContainerUtil.newArrayList();
    String[] lines = StringUtil.splitByLinesDontTrim(output);
    if (lines.length < 2) return ProcessInfo.EMPTY_ARRAY;

    String header = lines[0];
    int pidStart = header.indexOf("PID");
    if (pidStart == -1) return ProcessInfo.EMPTY_ARRAY;

    int statStart = header.indexOf(isMac ? "STAT" : "S", pidStart);
    if (statStart == -1) return ProcessInfo.EMPTY_ARRAY;

    int userStart = header.indexOf("USER", statStart);
    if (userStart == -1) return ProcessInfo.EMPTY_ARRAY;

    int commandStart = header.indexOf("COMMAND", userStart);
    if (commandStart == -1) return ProcessInfo.EMPTY_ARRAY;

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
      result.add(new ProcessInfo(pid, executablePath, args, user, state));
    }

    return result.isEmpty() ? ProcessInfo.EMPTY_ARRAY : result.toArray(new ProcessInfo[result.size()]);
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
}