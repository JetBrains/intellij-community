/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;

/**
 * Utility class to terminate unix processes.
 *
 * @author traff
 */
public class UnixProcessManager {
  public static final int SIGINT = 2;
  public static final int SIGKILL = 9;

  private static CLib C_LIB;

  static {
    try {
      if (!Platform.isWindows()) {
        C_LIB = ((CLib)Native.loadLibrary("c", CLib.class));
      }
    }
    catch (Exception e) {
      C_LIB = null;
    }
  }

  private UnixProcessManager() {
  }

  public static void sendSignal(Process process, int signal) {
    int process_pid = getProcessPid(process);
    sendSignal(process_pid, signal);
  }

  public static void sendSignal(int pid, int signal) {
    checkCLib();
    C_LIB.kill(pid, signal);
  }

  private static void checkCLib() {
    if (C_LIB == null) {
      throw new IllegalStateException("System is not unix(couldn't load c library)");
    }
  }

  public static boolean sendSigIntToProcessTree(Process process) {
    return sendSignalToProcessTree(process, SIGINT);
  }

  public static boolean sendSigKillToProcessTree(Process process) {
    return sendSignalToProcessTree(process, SIGKILL);
  }

  /**
   * Sends signal to every child process of a tree root process
   *
   * @param process tree root process
   */
  public static boolean sendSignalToProcessTree(Process process, int signal) {
    checkCLib();

    int our_pid = C_LIB.getpid();
    int process_pid = getProcessPid(process);

    try {
      String[] psCmd = getPSCmd(false);
      Process p = Runtime.getRuntime().exec(psCmd);

      ProcessInfo processInfo = new ProcessInfo();

      @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
      BufferedReader stdInput = new BufferedReader(new
                                                     InputStreamReader(p.getInputStream()));
      BufferedReader stdError = new BufferedReader(new
                                                     InputStreamReader(p.getErrorStream()));

      List<Integer> childrenPids = Lists.newArrayList();

      boolean result;
      try {
        String s;
        stdInput.readLine(); //ps output header
        int foundPid = 0;
        while ((s = stdInput.readLine()) != null) {
          StringTokenizer st = new StringTokenizer(s, " ");

          int parent_pid = Integer.parseInt(st.nextToken());
          int pid = Integer.parseInt(st.nextToken());

          processInfo.register(pid, parent_pid);

          if (parent_pid == process_pid) {
            childrenPids.add(pid);
          }

          if (pid == process_pid) {
            if (parent_pid == our_pid) {
              foundPid = pid;
            }
            else {
              throw new IllegalStateException("process is not our child");
            }
          }
        }

        if (foundPid != 0) {
          processInfo.killProcTree(foundPid, signal);
          result = true;
        }
        else {
          for (Integer pid : childrenPids) {
            processInfo.killProcTree(pid, signal);
          }
          result = false;
        }

        StringBuffer errorStr = new StringBuffer();
        while ((s = stdError.readLine()) != null) {
          errorStr.append(s).append("\n");
        }
        if (errorStr.length() > 0) {
          throw new IllegalStateException("error:" + errorStr.toString());
        }
      }
      finally {
        stdInput.close();
        stdError.close();
      }
      return result;
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static int getProcessPid(Process process) {
    try {
      Field f = process.getClass().getDeclaredField("pid");
      f.setAccessible(true);
      return ((Number)f.get(process)).intValue();
    }
    catch (NoSuchFieldException e) {
      throw new IllegalStateException("system is not unix", e);
    }
    catch (IllegalAccessException e) {
      throw new IllegalStateException("system is not unix", e);
    }
  }

  public static String[] getPSCmd(boolean commandLineOnly) {
    if (SystemInfo.isLinux) {
      return new String[]{"ps", "-e", "e", "--format", commandLineOnly ? "%a" : "%P%p%a"};
    }
    else if (SystemInfo.isMac) {
      return new String[]{"ps", "-ax", "-E", "-o", commandLineOnly ? "command" : "ppid,pid,command"};
    }
    else {
      throw new IllegalStateException(System.getProperty("os.name") + " is not supported.");
    }
  }

  public static boolean containsMarker(@NotNull String environ, @NotNull String uid) {
    return environ.contains(uid);
  }

  @NotNull
  public static String readProcEnviron(int child_pid) throws FileNotFoundException {
    StringBuffer res = new StringBuffer();
    Scanner s = new Scanner(new File("/proc/" + child_pid + "/environ"));
    while (s.hasNextLine()) {
      res.append(s).append("\n");
    }
    return res.toString();
  }


  public interface CLib extends Library {
    int getpid();

    int kill(int pid, int signal);
  }

  private static class ProcessInfo {
    private Map<Integer, List<Integer>> BY_PARENT = Maps.newTreeMap(); // pid -> list of children pids

    public void register(Integer pid, Integer parentPid) {
      List<Integer> children = BY_PARENT.get(parentPid);
      if (children == null) children = Lists.newLinkedList();
      children.add(pid);
      BY_PARENT.put(parentPid, children);
    }

    void killProcTree(int pid, int signal) {
      List<Integer> children = BY_PARENT.get(pid);
      if (children != null) {
        for (int child : children) killProcTree(child, signal);
      }
      sendSignal(pid, signal);
    }
  }
}
