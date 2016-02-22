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
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
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
      if (SystemInfo.isMac) {
        result = getProcessList_Mac();
      }
      else {
        result = getProcessList_Unix();
      }
      if (result != null) return result;

      LOG.error("Cannot get process list");
    }
    else {
      LOG.error("Cannot get process list, unexpected platform: " + SystemInfo.OS_NAME);
    }
    return Collections.emptyList();
  }

  @Nullable
  private static List<ProcessInfo> parseCommandOutput(@NotNull List<String> command,
                                                      @NotNull NullableFunction<String, List<ProcessInfo>> parser) {
    String output;
    try {
      ProcessOutput processOutput = ExecUtil.execAndGetOutput(new GeneralCommandLine(command));
      int exitCode = processOutput.getExitCode();
      if (exitCode != 0) {
        LOG.error("Cannot get process list, 'ps' exited with code " + exitCode + ", stdout:\n" 
                  + processOutput.getStdout()
                  + "\nstderr:\n"
                  + processOutput.getStderr());
      }
      output = processOutput.getStdout();
    }
    catch (ExecutionException e) {
      LOG.error("Cannot get process list", e);                                                                                                                                                                                                   
      return null;
    }
    return parser.fun(output);
  }

  @Nullable
  private static List<ProcessInfo> getProcessList_Unix() {
    File proc = new File("/proc");

    File[] processes = proc.listFiles();
    if (processes == null) {
      LOG.error("Cannot read /proc, not mounted?");                                                                                                                                                                                                   
      return null;
    }

    List<ProcessInfo> result = new ArrayList<ProcessInfo>();

    for (File each : processes) {
      int pid = StringUtil.parseInt(each.getName(), -1);
      if (pid == -1) continue;

      List<String> cmdline;
      try {
        FileInputStream stream = new FileInputStream(new File(each, "cmdline"));
        try {
          //noinspection SSBasedInspection - no better candidate for system encoding anyways 
          String cmdlineString = new String(FileUtil.loadBytes(stream));
          cmdline = StringUtil.split(cmdlineString, "\0");
        }
        finally {
          stream.close();
        }
      }
      catch (IOException e) {
        continue;
      }
      if (cmdline.isEmpty()) continue;

      result.add(new ProcessInfo(pid, StringUtil.join(cmdline, " "),
                                 PathUtil.getFileName(cmdline.get(0)),
                                 StringUtil.join(cmdline.subList(1, cmdline.size()), " ")
      ));
    }
    return result;
  }

  @Nullable
  private static List<ProcessInfo> getProcessList_Mac() {
    // In order to correctly determine executable file name and retrieve arguments from the command line
    // we need first to get the executable from 'comm' parameter, and then subtract it from the 'command' parameter.
    // Example:
    // 12  S user ./command
    // 12  S user ./command argument list

    return parseCommandOutput(Arrays.asList("/bin/ps", "-a", "-x", "-o", "pid,state,user,comm"),
                              new NullableFunction<String, List<ProcessInfo>>() {
                                @Nullable
                                @Override
                                public List<ProcessInfo> fun(final String commandOnly) {
                                  return parseCommandOutput(Arrays.asList("/bin/ps", "-a", "-x", "-o", "pid,state,user,command"),
                                                            new NullableFunction<String, List<ProcessInfo>>() {
                                                              @Nullable
                                                              @Override
                                                              public List<ProcessInfo> fun(String full) {
                                                                return parseMacOutput(commandOnly, full);
                                                              }
                                                            });
                                }
                              });
  }


  @Nullable
  static List<ProcessInfo> parseMacOutput(String commandOnly, String full) {
    List<MacProcessInfo> commands = doParseMacOutput(commandOnly);
    List<MacProcessInfo> fulls = doParseMacOutput(full);
    if (commands == null || fulls == null) return null;

    TIntObjectHashMap<String> idToCommand = new TIntObjectHashMap<String>();
    for (MacProcessInfo each : commands) {
      idToCommand.put(each.pid, each.commandLine);
    }

    List<ProcessInfo> result = new ArrayList<ProcessInfo>();
    for (MacProcessInfo each : fulls) {
      if (!idToCommand.containsKey(each.pid)) continue;

      String command = idToCommand.get(each.pid);
      if (!(each.commandLine.equals(command) || each.commandLine.startsWith(command + " "))) continue;

      String name = PathUtil.getFileName(command);
      String args = each.commandLine.substring(command.length()).trim();

      result.add(new ProcessInfo(each.pid, each.commandLine, name, args));
    }
    return result;
  }

  @Nullable
  private static List<MacProcessInfo> doParseMacOutput(String output) {
    List<MacProcessInfo> result = ContainerUtil.newArrayList();
    String[] lines = StringUtil.splitByLinesDontTrim(output);
    if (lines.length == 0) return null;

    String header = lines[0];
    int pidStart = header.indexOf("PID");
    if (pidStart == -1) return null;

    int statStart = header.indexOf("S", pidStart);
    if (statStart == -1) return null;

    int userStart = header.indexOf("USER", statStart);
    if (userStart == -1) return null;

    int commandStart = header.indexOf("COMM", userStart);
    if (commandStart == -1) return null;

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];

      int pid = StringUtil.parseInt(line.substring(0, statStart).trim(), -1);
      if (pid == -1) continue;

      String state = line.substring(statStart, userStart).trim();
      if (state.contains("Z")) continue; // zombie

      String user = line.substring(userStart, commandStart).trim();
      String commandLine = line.substring(commandStart).trim();

      result.add(new MacProcessInfo(pid, commandLine, user, state));
    }
    return result;
  }

  private static class MacProcessInfo {
    final int pid;
    final String commandLine;
    final String user;
    final String state;

    public MacProcessInfo(int pid, String commandLine, String user, String state) {
      this.pid = pid;
      this.commandLine = commandLine;
      this.user = user;
      this.state = state;
    }
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

      result.add(new ProcessInfo(pid, commandLine, name, args));
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

        result.add(new ProcessInfo(pid, name, name, ""));
      }
    }
    catch (IOException e) {
      LOG.error("Cannot parse listtasks output", e);
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