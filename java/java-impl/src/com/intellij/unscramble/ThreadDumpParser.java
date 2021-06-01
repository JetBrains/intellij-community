// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.unscramble;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class ThreadDumpParser {
  private static final Pattern ourThreadStartPattern = Pattern.compile("^\"(.+)\".+(prio=\\d+ (?:os_prio=[^\\s]+ )?.*tid=[^\\s]+ nid=[^\\s]+|[Ii][Dd]=\\d+) ([^\\[]+)");
  private static final Pattern ourForcedThreadStartPattern = Pattern.compile("^Thread (\\d+): \\(state = (.+)\\)");
  private static final Pattern ourYourkitThreadStartPattern = Pattern.compile("(.+) \\[([A-Z_, ]*)]");
  private static final Pattern ourYourkitThreadStartPattern2 = Pattern.compile("(.+) (?:State:)? (.+) CPU usage on sample: .+");
  private static final Pattern ourThreadStatePattern = Pattern.compile("java\\.lang\\.Thread\\.State: (.+) \\((.+)\\)");
  private static final Pattern ourThreadStatePattern2 = Pattern.compile("java\\.lang\\.Thread\\.State: (.+)");
  private static final Pattern ourWaitingForLockPattern = Pattern.compile("- waiting (on|to lock) <(.+)>");
  private static final Pattern ourParkingToWaitForLockPattern = Pattern.compile("- parking to wait for {2}<(.+)>");
  @NonNls private static final String PUMP_EVENT = "java.awt.EventDispatchThread.pumpOneEventForFilters";
  private static final Pattern ourIdleTimerThreadPattern = Pattern.compile("java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at java\\.util\\.TimerThread\\.mainLoop");
  private static final Pattern ourIdleSwingTimerThreadPattern = Pattern.compile("java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at javax\\.swing\\.TimerQueue\\.run");
  private static final String AT_JAVA_LANG_OBJECT_WAIT = "at java.lang.Object.wait(";

  private ThreadDumpParser() {
  }

  @NotNull
  public static List<ThreadState> parse(String threadDump) {
    List<ThreadState> result = new ArrayList<>();
    StringBuilder lastThreadStack = new StringBuilder();
    ThreadState lastThreadState = null;
    boolean expectingThreadState = false;
    boolean haveNonEmptyStackTrace = false;
    for(@NonNls String line: StringUtil.tokenize(threadDump, "\r\n")) {
      if (line.startsWith("============") || line.contains("Java-level deadlock")) {
        break;
      }
      ThreadState state = tryParseThreadStart(line.trim());
      if (state != null) {
        if (lastThreadState != null) {
          lastThreadState.setStackTrace(lastThreadStack.toString(), !haveNonEmptyStackTrace);
        }
        lastThreadState = state;
        result.add(lastThreadState);
        lastThreadStack.setLength(0);
        haveNonEmptyStackTrace = false;
        lastThreadStack.append(line).append("\n");
        expectingThreadState = true;
      }
      else {
        boolean parsedThreadState = false;
        if (expectingThreadState) {
          expectingThreadState = false;
          parsedThreadState = tryParseThreadState(line, lastThreadState);
        }
        lastThreadStack.append(line).append("\n");
        if (!parsedThreadState && line.trim().startsWith("at")) {
          haveNonEmptyStackTrace = true;
        }
      }
    }
    if (lastThreadState != null) {
      lastThreadState.setStackTrace(lastThreadStack.toString(), !haveNonEmptyStackTrace);
    }
    for(ThreadState threadState: result) {
      inferThreadStateDetail(threadState);

      String lockId = findWaitingForLock(threadState.getStackTrace());
      ThreadState lockOwner = findLockOwner(result, lockId, true);
      if (lockOwner == null) {
        lockOwner = findLockOwner(result, lockId, false);
      }
      if (lockOwner != null) {
        if (threadState.isAwaitedBy(lockOwner)) {
          threadState.addDeadlockedThread(lockOwner);
          lockOwner.addDeadlockedThread(threadState);
        }
        lockOwner.addWaitingThread(threadState);
      }
    }
    sortThreads(result);
    return result;
  }

  @Nullable
  private static ThreadState findLockOwner(List<? extends ThreadState> result, @Nullable String lockId, boolean ignoreWaiting) {
    if (lockId == null) return null;

    final String marker = "- locked <" + lockId + ">";
    for(ThreadState lockOwner : result) {
      String trace = lockOwner.getStackTrace();
      if (trace.contains(marker) && (!ignoreWaiting || !trace.contains(AT_JAVA_LANG_OBJECT_WAIT))) {
        return lockOwner;
      }
    }
    return null;
  }

  public static void sortThreads(List<? extends ThreadState> result) {
    result.sort((o1, o2) -> getInterestLevel(o2) - getInterestLevel(o1));
  }

  @Nullable
  private static String findWaitingForLock(final String stackTrace) {
    Matcher m = ourWaitingForLockPattern.matcher(stackTrace);
    if (m.find()) {
      return m.group(2);
    }
    m = ourParkingToWaitForLockPattern.matcher(stackTrace);
    if (m.find()) {
      return m.group(1);
    }
    return null;
  }

  private static int getInterestLevel(final ThreadState state) {
    if (state.isEmptyStackTrace()) return -10;
    if (state.isKnownJDKThread()) return -5;
    if (state.isSleeping()) {
      return -2;
    }
    if (state.getOperation() == ThreadOperation.Socket) {
      return -1;
    }
    return state.getStackDepth();
  }

  static boolean isKnownJdkThread(@NotNull String stackTrace) {
    return stackTrace.contains("java.lang.ref.Reference$ReferenceHandler.run") ||
        stackTrace.contains("java.lang.ref.Finalizer$FinalizerThread.run") ||
        stackTrace.contains("sun.awt.AWTAutoShutdown.run") ||
        stackTrace.contains("sun.java2d.Disposer.run") ||
        stackTrace.contains("sun.awt.windows.WToolkit.eventLoop") ||
        ourIdleTimerThreadPattern.matcher(stackTrace).find() ||
        ourIdleSwingTimerThreadPattern.matcher(stackTrace).find();
  }

  public static void inferThreadStateDetail(final ThreadState threadState) {
    @NonNls String stackTrace = threadState.getStackTrace();
    if (stackTrace.contains("at java.net.PlainSocketImpl.socketAccept") ||
        stackTrace.contains("at java.net.PlainDatagramSocketImpl.receive") ||
        stackTrace.contains("at java.net.SocketInputStream.socketRead") ||
        stackTrace.contains("at java.net.PlainSocketImpl.socketConnect")) {
      threadState.setOperation(ThreadOperation.Socket);
    }
    else if (stackTrace.contains("at java.io.FileInputStream.readBytes")) {
      threadState.setOperation(ThreadOperation.IO);
    }
    else if (stackTrace.contains("at java.lang.Thread.sleep")) {
      final String javaThreadState = threadState.getJavaThreadState();
      if (!Thread.State.RUNNABLE.name().equals(javaThreadState)) {
        threadState.setThreadStateDetail("sleeping");   // JDK 1.6 sets this explicitly, but JDK 1.5 does not
      }
    }
    if (threadState.isEDT()) {
      if (stackTrace.contains("java.awt.EventQueue.getNextEvent")) {
        threadState.setThreadStateDetail("idle");
      }
      int modality = 0;
      int pos = 0;
      while(true) {
        pos = stackTrace.indexOf(PUMP_EVENT, pos);
        if (pos < 0) break;
        modality++;
        pos += PUMP_EVENT.length();
      }
      threadState.setExtraState("modality level " + modality);
    }
  }

  @Nullable
  private static ThreadState tryParseThreadStart(String line) {
    Matcher m = ourThreadStartPattern.matcher(line);
    if (m.find()) {
      final ThreadState state = new ThreadState(m.group(1), m.group(3));
      if (line.contains(" daemon ")) {
        state.setDaemon(true);
      }
      return state;
    }

    m = ourForcedThreadStartPattern.matcher(line);
    if (m.matches()) {
      return new ThreadState(m.group(1), m.group(2));
    }

    boolean daemon = line.contains(" [DAEMON]");
    if (daemon) {
      line = StringUtil.replace(line, " [DAEMON]", "");
    }

    m = matchYourKit(line);
    if (m != null) {
      ThreadState state = new ThreadState(m.group(1), m.group(2));
      state.setDaemon(daemon);
      return state;
    }
    return null;
  }

  @Nullable
  private static Matcher matchYourKit(String line) {
    if (line.contains("[")) {
      Matcher m = ourYourkitThreadStartPattern.matcher(line);
      if (m.matches()) return m;
    }

    if (line.contains("CPU usage on sample:")) {
      Matcher m = ourYourkitThreadStartPattern2.matcher(line);
      if (m.matches()) return m;
    }

    return null;
  }

  private static boolean tryParseThreadState(final String line, final ThreadState threadState) {
    Matcher m = ourThreadStatePattern.matcher(line);
    if (m.find()) {
      threadState.setJavaThreadState(m.group(1));
      threadState.setThreadStateDetail(m.group(2).trim());
      return true;
    }
    m = ourThreadStatePattern2.matcher(line);
    if (m.find()) {
      threadState.setJavaThreadState(m.group(1));
      return true;
    }
    return false;
  }
}
