/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author yole
 */
public class PerformanceWatcher implements ApplicationComponent {
  private Thread myThread;
  private int myLoopCounter;
  private int mySwingThreadCounter;
  private final Semaphore myShutdownSemaphore = new Semaphore(1);
  private ThreadMXBean myThreadMXBean;
  private final DateFormat myDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
  //private DateFormat myPrintDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
  private File myLogDir;
  private int myUnresponsiveDuration = 0;
  private File myCurHangLogDir;
  private List<StackTraceElement> myStacktraceCommonPart;

  private int UNRESPONSIVE_THRESHOLD = 5;
  private int UNRESPONSIVE_INTERVAL = 5;

  public static PerformanceWatcher getInstance() {
    return ServiceManager.getService(PerformanceWatcher.class);
  }

  @NotNull
  public String getComponentName() {
    return "PerformanceWatcher";
  }

  public void initComponent() {
    myThreadMXBean = ManagementFactory.getThreadMXBean();

    if (shallNotWatch()) return;

    final String threshold = System.getProperty("performance.watcher.threshold");
    if (threshold != null) {
      try {
        UNRESPONSIVE_THRESHOLD = Integer.parseInt(threshold);
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }
    final String interval = System.getProperty("performance.watcher.interval");
    if (interval != null) {
      try {
        UNRESPONSIVE_INTERVAL = Integer.parseInt(interval);
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }
    if (UNRESPONSIVE_THRESHOLD == 0 || UNRESPONSIVE_INTERVAL == 0) {
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        deleteOldThreadDumps();
      }
    });

    myLogDir = new File(PathManager.getLogPath() + "/threadDumps-" + myDateFormat.format(new Date())
                        + "-" + ApplicationInfo.getInstance().getBuild().asString());
    myLogDir.mkdirs();
    myCurHangLogDir = myLogDir;

    try {
      myShutdownSemaphore.acquire();
    }
    catch (InterruptedException e) {
      // ignore
    }
    myThread = new Thread(new Runnable() {
      public void run() {
        checkEDTResponsiveness();
      }
    }, "Performance watcher");
    myThread.setPriority(Thread.MIN_PRIORITY);
    myThread.start();
  }

  private static void deleteOldThreadDumps() {
    File allLogsDir = new File(PathManager.getLogPath());
    if (allLogsDir.isDirectory()) {
      final String[] dirs = allLogsDir.list(new FilenameFilter() {
        public boolean accept(final File dir, final String name) {
          return name.startsWith("threadDumps-");
        }
      });
      if (dirs != null) {
        Arrays.sort(dirs);
        for (int i = 0; i < dirs.length - 11; i++) {
          FileUtil.delete(new File(allLogsDir, dirs [i]));
        }
      }
    }
  }

  public void disposeComponent() {
    if (shallNotWatch()) return;
    myShutdownSemaphore.release();
    try {
      myThread.join();
    }
    catch (InterruptedException e) {
      // ignore
    }
  }

  private boolean shallNotWatch() {
    return ApplicationManager.getApplication().isUnitTestMode() ||
           ApplicationManager.getApplication().isHeadlessEnvironment() ||
           UNRESPONSIVE_INTERVAL == 0 ||
           UNRESPONSIVE_THRESHOLD == 0;
  }

  private void checkEDTResponsiveness() {
    while(true) {
      try {
        if (myShutdownSemaphore.tryAcquire(UNRESPONSIVE_INTERVAL, TimeUnit.SECONDS)) {
          break;
        }
      }
      catch (InterruptedException e) {
        break;
      }
      if (mySwingThreadCounter != myLoopCounter) {
        myUnresponsiveDuration++;
        if (myUnresponsiveDuration == UNRESPONSIVE_THRESHOLD) {
          //System.out.println("EDT is not responding at " + myPrintDateFormat.format(new Date()));
          myCurHangLogDir = new File(myLogDir, myDateFormat.format(new Date()));
          myCurHangLogDir.mkdirs();
        }
        if (myUnresponsiveDuration >= UNRESPONSIVE_THRESHOLD) {
          dumpThreads(false);
        }
      }
      else {
        if (myUnresponsiveDuration >= UNRESPONSIVE_THRESHOLD) {
          //System.out.println("EDT was unresponsive for " + myUnresponsiveDuration + " seconds");
          myCurHangLogDir.renameTo(new File(myLogDir, getLogDirForHang()));
          myUnresponsiveDuration = 0;
          myCurHangLogDir = myLogDir;

          myStacktraceCommonPart = null;
        }
        myUnresponsiveDuration = 0;
      }
      myLoopCounter++;
      SwingUtilities.invokeLater(new SwingThreadRunnable(myLoopCounter));
    }
  }

  private String getLogDirForHang() {
    StringBuilder name = new StringBuilder(myCurHangLogDir.getName());
    name.append("-").append(myUnresponsiveDuration);
    if (myStacktraceCommonPart != null && !myStacktraceCommonPart.isEmpty()) {
      final StackTraceElement element = myStacktraceCommonPart.get(0);
      name.append("-").append(StringUtil.getShortName(element.getClassName())).append(".").append(element.getMethodName());
    }
    return name.toString();
  }

  public void dumpThreads(boolean millis) {
    final String suffix = millis ? "-" + String.valueOf(System.currentTimeMillis()) : "";
    File f = new File(myCurHangLogDir, "threadDump-" + myDateFormat.format(new Date()) + suffix + ".txt");
    FileOutputStream fos;
    try {
      fos = new FileOutputStream(f);
    }
    catch (FileNotFoundException e) {
      return;
    }
    OutputStreamWriter writer = new OutputStreamWriter(fos);
    try {
      dumpThreadsToFile(writer);
    }
    finally {
      try {
        writer.close();
      }
      catch (IOException e) {
        // ignore
      }
    }
  }

  public static void dumpThreadsToConsole(String message) {
    OutputStreamWriter writer = new OutputStreamWriter(System.err);
    try {
      writer.write(message);
      writer.write("\n");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    getInstance().dumpThreadsToFile(writer);
  }

  public static String dumpThreadsToString() {
    StringWriter writer = new StringWriter();
    getInstance().dumpThreadsToFile(writer);
    return writer.toString();
  }

  private void dumpThreadsToFile(final Writer f) {
    boolean dumpSuccessful = false;

    try {
      ThreadInfo[] threads = myThreadMXBean.dumpAllThreads(false, false);
      for(ThreadInfo info: threads) {
        if (info != null) {
          dumpThreadInfo(info, f);
        }
      }
      dumpSuccessful = true;
    }
    catch (Exception ignored) {

    }

    if (!dumpSuccessful) {
      final long[] threadIds = myThreadMXBean.getAllThreadIds();
      final ThreadInfo[] threadInfo = myThreadMXBean.getThreadInfo(threadIds, Integer.MAX_VALUE);
      for (ThreadInfo info : threadInfo) {
        if (info != null) {
          dumpThreadInfo(info, f);
        }
      }
    }
  }

  private void dumpThreadInfo(final ThreadInfo info, final Writer f) {
    StackTraceElement[] stackTraceElements = info.getStackTrace();
    dumpCallStack(info, f, stackTraceElements);
    if (info.getThreadName().equals("AWT-EventQueue-1")) {
      if (myStacktraceCommonPart == null) {
        myStacktraceCommonPart = new ArrayList<StackTraceElement>();
        Collections.addAll(myStacktraceCommonPart, stackTraceElements);
      }
      else {
        updateStacktraceCommonPart(stackTraceElements);
      }
    }
  }

  private void updateStacktraceCommonPart(final StackTraceElement[] stackTraceElements) {
    for(int i=0; i < myStacktraceCommonPart.size() && i < stackTraceElements.length; i++) {
      StackTraceElement el1 = myStacktraceCommonPart.get(myStacktraceCommonPart.size()-i-1);
      StackTraceElement el2 = stackTraceElements [stackTraceElements.length-i-1];
      if (!el1.equals(el2)) {
        myStacktraceCommonPart = myStacktraceCommonPart.subList(myStacktraceCommonPart.size() - i, myStacktraceCommonPart.size());
        break;
      }
    }
  }

  private static String getReadableState(Thread.State state) {
    switch (state) {
      case BLOCKED: return "blocked";
      case TIMED_WAITING:
      case WAITING: return "waiting on condition";
      case RUNNABLE: return "runnable";
      case NEW: return "new";
      case TERMINATED: return "terminated";
    }
    return null;
  }

  private static void dumpCallStack(final ThreadInfo info, final Writer f, final StackTraceElement[] stackTraceElements) {
    try {
      @NonNls StringBuilder sb = new StringBuilder("\"").append(info.getThreadName()).append("\"");
      sb.append(" prio=0 tid=0x0 nid=0x0 ").append(getReadableState(info.getThreadState())).append("\n");
      sb.append("     java.lang.Thread.State: ").append(info.getThreadState()).append("\n");
      if (info.getLockName() != null) {
        sb.append(" on ").append(info.getLockName());
      }
      if (info.getLockOwnerName() != null) {
        sb.append(" owned by \"").append(info.getLockOwnerName()).append("\" Id=").append(info.getLockOwnerId());
      }
      if (info.isSuspended()) {
          sb.append(" (suspended)");
      }
      if (info.isInNative()) {
          sb.append(" (in native)");
      }

      f.write(sb + "\n");
      for (StackTraceElement element : stackTraceElements) {
        f.write("\tat " + element.toString() + "\n");
      }
      f.write("\n");
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private class SwingThreadRunnable implements Runnable {
    private final int myCount;

    private SwingThreadRunnable(final int count) {
      myCount = count;
    }

    public void run() {
      mySwingThreadCounter = myCount;
    }
  }
}
