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
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * This implementation uses the tasklist.exe from windows (must be on the path).
 * 
 * Use through ProcessUtils.
 */
class ProcessListWin32 {
  private static final Logger LOG = Logger.getInstance(ProcessListWin32.class);

  public static ProcessInfo[] getProcessList() {
    try {
      return createFromWMIC();
    }
    catch (Exception e) {
      //Keep on going for other alternatives
    }

    try {
      Process p;
      try {
        String[] command = {"tasklist.exe", "/fo", "csv", "/nh", "/v"};
        p = ProcessUtils.createProcess(command, null, null);
      }
      catch (Exception e) {
        //Use fallback
        return ProcessListWin32Internal.getProcessList();
      }
      try {
        return parseListTasks(p.getInputStream());
      }
      finally {
        p.destroy();
      }
    }
    catch (IOException ignored) { }

    return ProcessInfo.EMPTY_ARRAY;
  }

  private static ProcessInfo[] createFromWMIC() throws Exception {
    @SuppressWarnings("SpellCheckingInspection") String[] command = {"wmic.exe", "path", "win32_process", "get", "Caption,Processid,Commandline"};
    Process p = ProcessUtils.createProcess(command, null, null);

    List<ProcessInfo> lst = new ArrayList<ProcessInfo>();
    BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
    try {
      String line = br.readLine();
      //We should have something as: Caption      CommandLine      ProcessId
      //From this we get the number of characters for each column
      int commandLineI = line.indexOf("CommandLine");
      int processIdI = line.indexOf("ProcessId");
      if (commandLineI == -1) {
        throw new AssertionError("Could not find CommandLine in: " + line);
      }
      if (processIdI == -1) {
        throw new AssertionError("Could not find ProcessId in: " + line);
      }

      while (true) {
        line = br.readLine();
        if (line == null) {
          break;
        }
        if (line.trim().length() == 0) {
          continue;
        }
        String name = line.substring(0, commandLineI).trim();
        String commandLine = line.substring(commandLineI, processIdI).trim();
        String processId = line.substring(processIdI, line.length()).trim();
        lst.add(new ProcessInfo(Integer.parseInt(processId), name, commandLine));
      }
      if (lst.size() == 0) {
        throw new AssertionError("Error: no processes found");
      }
      return lst.toArray(new ProcessInfo[lst.size()]);
    }
    catch (Exception e) {
      LOG.error(e);
      throw e;
    }
    finally {
      br.close();
    }
  }

  private static ProcessInfo[] parseListTasks(InputStream stream) throws IOException {
    List<ProcessInfo> processList = ContainerUtil.newArrayList();

    CSVReader reader = new CSVReader(new BufferedReader(new InputStreamReader(stream)));
    try {
      String[] next;
      do {
        try {
          next = reader.readNext();
          if (next != null) {
            int pid = Integer.parseInt(next[1]);
            String name = next[0] + " - " + next[next.length - 1];
            processList.add(new ProcessInfo(pid, name));
          }
        }
        catch (IOException e) {
          break;
        }
      }
      while (next != null);
    }
    finally {
      reader.close();
    }

    return processList.isEmpty() ? ProcessInfo.EMPTY_ARRAY : processList.toArray(new ProcessInfo[processList.size()]);
  }
}