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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;

import java.io.*;
import java.util.List;

/**
 * This implementation uses a listtasks which is shipped together (so, it should always work on windows).
 * 
 * Use through ProcessUtils.
 */
class ProcessListWin32Internal implements IProcessList {
  private static final Logger LOG = Logger.getInstance(ProcessListWin32Internal.class);

  @Override
  public ProcessInfo[] getProcessList() {
    String[] dirs = {
      PathManager.getBinPath(),
      PathManager.getHomePath() + "/community/bin/win", 
      PathManager.getBinPath() + "/win"};
    
    File listtasks = null;
    
    for (String each : dirs) {
      listtasks = new File(each, "listtasks.exe");
    }
    
    if (!listtasks.exists()) {
      LOG.error("listtasks.exe not found in bin folders");
      return ProcessInfo.EMPTY_ARRAY;
    }
    
    try {
      String[] command = {listtasks.getCanonicalPath()};
      Process p = ProcessUtils.createProcess(command, null, null);
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

  private static ProcessInfo[] parseListTasks(InputStream stream) throws IOException {
    List<ProcessInfo> processList = ContainerUtil.newArrayList();

    BufferedReader br = new BufferedReader(new InputStreamReader(stream));
    try {
      String line;
      while ((line = br.readLine()) != null) {
        int tab = line.indexOf('\t');
        if (tab != -1) {
          String proc = line.substring(0, tab).trim();
          String name = line.substring(tab).trim();
          if (proc.length() > 0 && name.length() > 0) {
            try {
              int pid = Integer.parseInt(proc);
              processList.add(new ProcessInfo(pid, name));
            }
            catch (NumberFormatException ignored) { }
          }
        }
      }
    }
    finally {
      br.close();
    }

    return processList.isEmpty() ? ProcessInfo.EMPTY_ARRAY : processList.toArray(new ProcessInfo[processList.size()]);
  }
}