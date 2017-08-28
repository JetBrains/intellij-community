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
import com.intellij.openapi.application.PathManager;
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

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class ProcessListUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.impl.ProcessListUtil");
  private static final String WIN_PROCESS_LIST_HELPER_FILENAME = "WinProcessListHelper.exe";

  @NotNull
  public static ProcessInfo[] getProcessList() {
    List<ProcessInfo> result = doGetProcessList();
    return result.isEmpty() ? ProcessInfo.EMPTY_ARRAY : result.toArray(new ProcessInfo[result.size()]);
  }

  @NotNull
  private static List<ProcessInfo> doGetProcessList() {
    List<ProcessInfo> result;
    if (SystemInfo.isWindows) {
      result = getProcessListUsingWinNativeHelper();
      if (result != null) return result;
      LOG.info("Cannot get process list via " + WIN_PROCESS_LIST_HELPER_FILENAME + ", fallback to wmic");

      result = getProcessListUsingWindowsWMIC();
      if (result != null) return result;

      LOG.info("Cannot get process list via wmic, fallback to tasklist");
      result = getProcessListUsingWindowsTaskList();
      if (result != null) return result;

      LOG.error("Cannot get process list via wmic and tasklist");
    }
    else if (SystemInfo.isUnix) {
      if (SystemInfo.isMac) {
        result = getProcessListOnMac();
      }
      else {
        result = getProcessListOnUnix();
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
        LOG.error("Cannot get process list, command '" + StringUtil.join(command, " ") +"' exited with code " + exitCode + ", stdout:\n"
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
  private static List<ProcessInfo> getProcessListOnUnix() {
    File proc = new File("/proc");

    File[] processes = proc.listFiles();
    if (processes == null) {
      LOG.error("Cannot read /proc, not mounted?");
      return null;
    }

    List<ProcessInfo> result = new ArrayList<>();

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

      String executablePath = null;

      try {
        File exe = new File(each, "exe");
        if (!exe.getAbsolutePath().equals(exe.getCanonicalPath())) {
          executablePath = exe.getCanonicalPath();
        }
      }
      catch (IOException e) {
        // couldn't resolve symlink
      }

      result.add(new ProcessInfo(pid, StringUtil.join(cmdline, " "),
                                 PathUtil.getFileName(cmdline.get(0)),
                                 StringUtil.join(cmdline.subList(1, cmdline.size()), " "),
                                 executablePath
      ));
    }
    return result;
  }

  @Nullable
  private static List<ProcessInfo> getProcessListOnMac() {
    // In order to correctly determine executable file name and retrieve arguments from the command line
    // we need first to get the executable from 'comm' parameter, and then subtract it from the 'command' parameter.
    // Example:
    // 12  S user ./command
    // 12  S user ./command argument list

    return parseCommandOutput(Arrays.asList("/bin/ps", "-a", "-x", "-o", "pid,state,user,comm"),
                              commandOnly -> parseCommandOutput(Arrays.asList("/bin/ps", "-a", "-x", "-o", "pid,state,user,command"),
                                                        full -> parseMacOutput(commandOnly, full)));
  }

  //command = `/bin/ps -a -x -o pid,state,user,comm` on Remote
  @Nullable
  public static List<ProcessInfo> parseMacProcessInfos(String commandOnly, String full) {
    List<ProcessInfo> ans = parseMacOutput(commandOnly, full);
    return ans;
  }

  @Nullable
  public static List<ProcessInfo> parseLinuxProcessInfos(String commandOnly, String full) {
    return null;//parseLinuxOutput(commandOnly, full);
  }


  @Nullable
  static List<ProcessInfo> parseMacOutput(String commandOnly, String full) {
    List<MacProcessInfo> commands = doParseMacOutput(commandOnly);
    List<MacProcessInfo> fulls = doParseMacOutput(full);
    if (commands == null || fulls == null) return null;

    TIntObjectHashMap<String> idToCommand = new TIntObjectHashMap<>();
    for (MacProcessInfo each : commands) {
      idToCommand.put(each.pid, each.commandLine);
    }

    List<ProcessInfo> result = new ArrayList<>();
    for (MacProcessInfo each : fulls) {
      if (!idToCommand.containsKey(each.pid)) continue;

      String command = idToCommand.get(each.pid);
      if (!(each.commandLine.equals(command) || each.commandLine.startsWith(command + " "))) continue;

      String name = PathUtil.getFileName(command);
      String args = each.commandLine.substring(command.length()).trim();

      result.add(new ProcessInfo(each.pid, each.commandLine, name, args, command));
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

      try {
        int pid = StringUtil.parseInt(line.substring(0, statStart).trim(), -1);
        if (pid == -1) continue;

        String state = line.substring(statStart, userStart).trim();
        if (state.contains("Z")) continue; // zombie

        String user = line.substring(userStart, commandStart).trim();
        String commandLine = line.substring(commandStart).trim();

        result.add(new MacProcessInfo(pid, commandLine, user, state));
      }
      catch (Exception e) {
        LOG.error("Can't parse line '" + line + "'", e);
      }
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

  private static List<ProcessInfo> getProcessListUsingWinNativeHelper() {
    try {
      File nativeHelper = findNativeHelper();
      return parseCommandOutput(Collections.singletonList(nativeHelper.getAbsolutePath()), ProcessListUtil::parseWinNativeHelperOutput);
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
      return null;
    }
  }

  private static List<ProcessInfo> parseWinNativeHelperOutput(String output) throws IllegalStateException {
    String[] strings = StringUtil.splitByLines(output, false);
    ArrayList<ProcessInfo> result = new ArrayList<>();
    int processCount = strings.length / 3;
    for (int i = 0; i < processCount; i++) {
      int offset = i * 3;
      int id = StringUtil.parseInt(strings[offset], -1);
      if (id == -1 || id == 0) continue;

      String name = strings[offset + 1];
      if (StringUtil.isEmpty(name)) continue;

      String commandLine = strings[offset + 2];
      String args;
      if (commandLine.isEmpty()) {
        commandLine = name;
        args = "";
      }
      else {
        args = extractCommandLineArgs(commandLine, name);
      }
      result.add(new ProcessInfo(id, commandLine, name, args));
    }
    return result;
  }

  private static String extractCommandLineArgs(String fullCommandLine, String executableName) {
    List<String> commandLineList = StringUtil.splitHonorQuotes(fullCommandLine, ' ');
    if (commandLineList.isEmpty()) return "";

    String first = StringUtil.unquoteString(commandLineList.get(0));
    if (StringUtil.endsWithIgnoreCase(first, executableName)) {
      List<String> argsList = commandLineList.subList(1, commandLineList.size());
      return StringUtil.join(argsList, " ");
    }
    return "";
  }

  private static File findNativeHelper() throws FileNotFoundException {
    return PathManager.findBinFileWithException(WIN_PROCESS_LIST_HELPER_FILENAME);
  }

  @Nullable
  static List<ProcessInfo> getProcessListUsingWindowsWMIC() {
    return parseCommandOutput(Arrays.asList("wmic.exe", "path", "win32_process", "get", "Caption,Processid,Commandline,ExecutablePath"),
                              output -> parseWMICOutput(output));
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

    int executablePathStart = header.indexOf("ExecutablePath");
    if (executablePathStart == -1) return null;


    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];

      int pid = StringUtil.parseInt(line.substring(pidStart, line.length()).trim(), -1);
      if (pid == -1 || pid == 0) continue;

      String executablePath = line.substring(executablePathStart, pidStart).trim();

      String name = line.substring(0, commandLineStart).trim();
      if (name.isEmpty()) continue;

      String commandLine = line.substring(commandLineStart, executablePathStart).trim();
      String args = "";

      if (commandLine.isEmpty()) {
        commandLine = name;
      }
      else {
        args = extractCommandLineArgs(commandLine, name);
      }

      result.add(new ProcessInfo(pid, commandLine, name, args, executablePath));
    }
    return result;
  }

  @Nullable
  static List<ProcessInfo> getProcessListUsingWindowsTaskList() {
    return parseCommandOutput(Arrays.asList("tasklist.exe", "/fo", "csv", "/nh", "/v"),
                              output -> parseListTasksOutput(output));
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