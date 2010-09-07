package com.intellij.execution.process;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/**
 * This runner manages ctrl+break(ctrl+c) termination of process.
 *
 * @author traff
 */
public class RunnerMediator {
  private static final int SIGINT = 2;
  private static final String UID_KEY_NAME = "PROCESSUUID";

  private RunnerMediator() {
  }

  public static String getRunnerPath() {
    if (File.separatorChar == '\\') {
      return "runnerw.exe";
    }
    else {
      throw new IllegalStateException("There is no need of runner under unix based OS");
    }
  }

  public static void injectRunnerCommand(GeneralCommandLine commandLine) {
    commandLine.getParametersList().addAt(0, commandLine.getExePath());
    commandLine.setExePath(getRunnerPath());
  }

  public static String injectUid(GeneralCommandLine commandLine) {
    String uid = commandLine.getExePath() + ":" + UUID.randomUUID().toString();
    commandLine.getEnvParams().put(UID_KEY_NAME, uid);
    return uid;
  }

  public static ColoredProcessHandler createProcessWithStopInjections(GeneralCommandLine commandLine)
    throws ExecutionException {
    if (isWindows()) {
      injectRunnerCommand(commandLine);
    }

    String processUid = injectUid(commandLine);

    Process p = commandLine.createProcess();

    return new CustomDestroyProcessHandler(p, commandLine, processUid);
  }

  public static class CustomDestroyProcessHandler extends ColoredProcessHandler {
    private static final char IAC = (char)5;
    private static final char BRK = (char)3;

    private final String myProcessUid;
    private final String myCommand;


    public CustomDestroyProcessHandler(@NotNull Process process,
                                       @NotNull GeneralCommandLine commandLine,
                                       @NotNull String processUid) {
      super(process, commandLine.getCommandLineString());
      myProcessUid = processUid;
      myCommand = commandLine.getExePath();
    }

    @Override
    protected void destroyProcessImpl() {
      if (isWindows()) {
        sendCtrlBreakThroughStream();
      }
      else if (SystemInfo.isLinux || SystemInfo.isMac) {
        killProcess();
      }
      else {
        super.destroyProcessImpl();
      }
    }

    public void killProcess() {
      sendSigInt(myProcessUid);
    }

    private void sendCtrlBreakThroughStream() {
      OutputStream os = getProcessInput();
      PrintWriter pw = new PrintWriter(os);
      try {
        pw.print(IAC);
        pw.print(BRK);
        pw.flush();
      }
      finally {
        pw.close();
      }
    }
  }

  public static boolean isWindows() {
    if (File.separatorChar == '\\') {
      return true;
    }
    return false;
  }


  private static void sendSigInt(String child_uid) {
    if (C_LIB == null) {
      throw new IllegalStateException("no CLIB");
    }
    int our_pid = C_LIB.getpid();

    try {
      String[] psCmd = getCmd();
      Process p = Runtime.getRuntime().exec(psCmd);

      @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
      BufferedReader stdInput = new BufferedReader(new
                                                     InputStreamReader(p.getInputStream()));

      BufferedReader stdError = new BufferedReader(new
                                                     InputStreamReader(p.getErrorStream()));
      try {
        String s;
        stdInput.readLine(); //ps output header
        int foundPid = 0;
        while ((s = stdInput.readLine()) != null) {
          StringTokenizer st = new StringTokenizer(s, " ");

          int parent_pid = Integer.parseInt(st.nextToken());
          int pid = Integer.parseInt(st.nextToken());

          ProcessInfo.register(pid, parent_pid);

          if (parent_pid == our_pid) {
            if (containsMarker(s, child_uid)) {
              foundPid = pid;
            }
          }
        }

        if (foundPid != 0) {
          ProcessInfo.killProcTree(foundPid);
        }
        else {
          throw new IllegalStateException("process not found: " + our_pid + ", uid=" + child_uid);
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
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String[] getCmd() {
    if (SystemInfo.isLinux) {
      return new String[]{"ps", "e", "--format", "%P%p%a"};
    }
    else if (SystemInfo.isMac) {
      return new String[]{"ps", "-ax", "-E", "-o", "ppid,pid,command"};
    }
    else {
      throw new IllegalStateException(System.getProperty("os.name") + " is not supported.");
    }
  }

  private static boolean containsMarker(@NotNull String environ, @NotNull String uid) {
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

  private static void sendSigInt(int pid) {
    C_LIB.kill(pid, SIGINT);
  }

  interface CLib extends Library {
    int getpid();

    int kill(int pid, int signal);
  }

  static CLib C_LIB;

  static {
    try {
      if (!Platform.isWindows()) {

        C_LIB = ((CLib)Native.loadLibrary("c", CLib.class));
      }
    }
    catch (Exception e) {
      //TODO: make proper exception handling
    }
  }

  private static class ProcessInfo {

    private ProcessInfo() {
    }

    private static Map<Integer, List<Integer>> BY_PARENT = Maps.newTreeMap(); // pid -> list of children pids

    public static void register(Integer pid, Integer parentPid) {
      List<Integer> children = BY_PARENT.get(parentPid);
      if (children == null) children = Lists.newLinkedList();
      children.add(pid);
      BY_PARENT.put(parentPid, children);
    }

    static void killProcTree(int pid) {
      List<Integer> children = BY_PARENT.get(pid);
      if (children != null) {
        for (int child : children) killProcTree(child);
      }
      sendSigInt(pid);
    }

  }
}
