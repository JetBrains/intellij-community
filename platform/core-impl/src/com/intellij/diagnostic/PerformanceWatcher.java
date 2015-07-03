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
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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
  private File mySessionLogDir;
  private int myUnresponsiveDuration;
  private File myCurHangLogDir;
  private List<StackTraceElement> myStacktraceCommonPart;

  /**
   * If the product is unresponsive for UNRESPONSIVE_THRESHOLD seconds, dump threads every UNRESPONSIVE_INTERVAL seconds
   */
  private int UNRESPONSIVE_THRESHOLD_SECONDS = 5;
  private int UNRESPONSIVE_INTERVAL_SECONDS = 5;

  public static PerformanceWatcher getInstance() {
    return ServiceManager.getService(PerformanceWatcher.class);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "PerformanceWatcher";
  }

  @Override
  public void initComponent() {
    myThreadMXBean = ManagementFactory.getThreadMXBean();

    if (!shouldWatch()) return;

    final String threshold = System.getProperty("performance.watcher.threshold");
    if (threshold != null) {
      try {
        UNRESPONSIVE_THRESHOLD_SECONDS = Integer.parseInt(threshold);
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }
    final String interval = System.getProperty("performance.watcher.interval");
    if (interval != null) {
      try {
        UNRESPONSIVE_INTERVAL_SECONDS = Integer.parseInt(interval);
      }
      catch (NumberFormatException e) {
        // ignore
      }
    }
    if (UNRESPONSIVE_THRESHOLD_SECONDS == 0 || UNRESPONSIVE_INTERVAL_SECONDS == 0) {
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        deleteOldThreadDumps();
      }
    });

    mySessionLogDir = new File(PathManager.getLogPath() + "/threadDumps-" + myDateFormat.format(new Date())
                        + "-" + ApplicationInfo.getInstance().getBuild().asString());
    myCurHangLogDir = mySessionLogDir;

    try {
      myShutdownSemaphore.acquire();
    }
    catch (InterruptedException e) {
      // ignore
    }
    myThread = new Thread(new Runnable() {
      @Override
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
        @Override
        public boolean accept(@NotNull final File dir, @NotNull final String name) {
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

  @Override
  public void disposeComponent() {
    if (!shouldWatch()) return;
    myShutdownSemaphore.release();
    try {
      myThread.join();
    }
    catch (InterruptedException e) {
      // ignore
    }
  }

  private boolean shouldWatch() {
    return !ApplicationManager.getApplication().isUnitTestMode() &&
           !ApplicationManager.getApplication().isHeadlessEnvironment() &&
           UNRESPONSIVE_INTERVAL_SECONDS != 0 &&
           UNRESPONSIVE_THRESHOLD_SECONDS != 0;
  }

  private void checkEDTResponsiveness() {
    while(true) {
      try {
        if (myShutdownSemaphore.tryAcquire(UNRESPONSIVE_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
          break;
        }
      }
      catch (InterruptedException e) {
        break;
      }
      if (mySwingThreadCounter != myLoopCounter) {
        myUnresponsiveDuration += UNRESPONSIVE_INTERVAL_SECONDS;
        if (myUnresponsiveDuration >= UNRESPONSIVE_THRESHOLD_SECONDS) {
          if (myCurHangLogDir == mySessionLogDir) {
            //System.out.println("EDT is not responding at " + myPrintDateFormat.format(new Date()));
            myCurHangLogDir = new File(mySessionLogDir, myDateFormat.format(new Date()));
          }
          dumpThreads("", false);
        }
      }
      else {
        if (myUnresponsiveDuration >= UNRESPONSIVE_THRESHOLD_SECONDS) {
          //System.out.println("EDT was unresponsive for " + myUnresponsiveDuration + " seconds");
          if (myCurHangLogDir != mySessionLogDir && myCurHangLogDir.exists()) {
            myCurHangLogDir.renameTo(new File(mySessionLogDir, getLogDirForHang()));
          }
          myUnresponsiveDuration = 0;
          myCurHangLogDir = mySessionLogDir;

          myStacktraceCommonPart = null;
        }
        myUnresponsiveDuration = 0;
      }
      myLoopCounter++;
      SwingUtilities.invokeLater(new SwingThreadRunnable(myLoopCounter));
    }
  }

  private String getLogDirForHang() {
    StringBuilder name = new StringBuilder("freeze-" + myCurHangLogDir.getName());
    name.append("-").append(myUnresponsiveDuration);
    if (myStacktraceCommonPart != null && !myStacktraceCommonPart.isEmpty()) {
      final StackTraceElement element = myStacktraceCommonPart.get(0);
      name.append("-").append(StringUtil.getShortName(element.getClassName())).append(".").append(element.getMethodName());
    }
    return name.toString();
  }

  @Nullable
  public File dumpThreads(@NotNull String pathPrefix, boolean millis) {
    if (!shouldWatch()) return null;

    String suffix = millis ? "-" + System.currentTimeMillis() : "";
    File file = new File(myCurHangLogDir, pathPrefix + "threadDump-" + myDateFormat.format(new Date()) + suffix + ".txt");

    File dir = file.getParentFile();
    if (!(dir.isDirectory() || dir.mkdirs())) {
      return null;
    }

    try {
      OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file));
      try {
        StackTraceElement[] edtStack = ThreadDumper.dumpThreadsToFile(myThreadMXBean, writer);
        if (edtStack != null) {
          if (myStacktraceCommonPart == null) {
            myStacktraceCommonPart = ContainerUtil.newArrayList(edtStack);
          }
          else {
            updateStacktraceCommonPart(edtStack);
          }
        }
      }
      finally {
        writer.close();
      }
    }
    catch (IOException ignored) { }
    return file;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
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

    @Override
    public void run() {
      mySwingThreadCounter = myCount;
    }
  }
}
