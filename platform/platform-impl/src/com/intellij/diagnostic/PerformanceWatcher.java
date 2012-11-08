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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.lang.management.ManagementFactory;
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

  /**
   * If the product is unresponsive for UNRESPONSIVE_THRESHOLD seconds, dump threads every UNRESPONSIVE_INTERVAL seconds
   */
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
        myUnresponsiveDuration += UNRESPONSIVE_INTERVAL;
        if (myUnresponsiveDuration == UNRESPONSIVE_THRESHOLD) {
          //System.out.println("EDT is not responding at " + myPrintDateFormat.format(new Date()));
          myCurHangLogDir = new File(myLogDir, myDateFormat.format(new Date()));
        }
        if (myUnresponsiveDuration >= UNRESPONSIVE_THRESHOLD) {
          dumpThreads(false);
        }
      }
      else {
        if (myUnresponsiveDuration >= UNRESPONSIVE_THRESHOLD) {
          //System.out.println("EDT was unresponsive for " + myUnresponsiveDuration + " seconds");
          if (myCurHangLogDir.exists()) {
            myCurHangLogDir.renameTo(new File(myLogDir, getLogDirForHang()));
          }
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
    if (shallNotWatch()) return;

    final String suffix = millis ? "-" + String.valueOf(System.currentTimeMillis()) : "";
    myCurHangLogDir.mkdirs();

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
      final StackTraceElement[] edtStack = ThreadDumper.dumpThreadsToFile(myThreadMXBean, writer);
      if (edtStack != null) {
        if (myStacktraceCommonPart == null) {
          myStacktraceCommonPart = new ArrayList<StackTraceElement>();
          Collections.addAll(myStacktraceCommonPart, edtStack);
        }
        else {
          updateStacktraceCommonPart(edtStack);
        }
      }
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
    ThreadDumper.dumpThreadsToFile(getInstance().myThreadMXBean, writer);
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
