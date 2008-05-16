package com.intellij.diagnostic;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.Date;

/**
 * @author yole
 */
public class PerformanceWatcher implements ApplicationComponent {
  private Thread myThread;
  private int myLoopCounter;
  private int mySwingThreadCounter;
  private Semaphore myShutdownSemaphore = new Semaphore(1);
  private ThreadMXBean myThreadMXBean;
  private Method myDumpAllThreadsMethod;
  private DateFormat myDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
  private File myLogDir;

  @NotNull
  public String getComponentName() {
    return "PerformanceWatcher";
  }

  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    myLogDir = new File(PathManager.getSystemPath() + "/log/threadDumps-" + myDateFormat.format(new Date()));
    myLogDir.mkdirs();
    myThreadMXBean = ManagementFactory.getThreadMXBean();
    // this method was added in JDK 1.6 so we have to all it through reflection
    try {
      myDumpAllThreadsMethod = ThreadMXBean.class.getMethod("dumpAllThreads", boolean.class, boolean.class);
    }
    catch (NoSuchMethodException e) {
      myDumpAllThreadsMethod = null;
    }

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
    myThread.start();
  }

  public void disposeComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    myShutdownSemaphore.release();
    try {
      myThread.join();
    }
    catch (InterruptedException e) {
      // ignore
    }
  }

  private void checkEDTResponsiveness() {
    while(true) {
      try {
        if (myShutdownSemaphore.tryAcquire(1, TimeUnit.SECONDS)) {
          break;
        }
      }
      catch (InterruptedException e) {
        break;
      }
      if (mySwingThreadCounter != myLoopCounter) {
        dumpThreads();
      }
      myLoopCounter++;
      SwingUtilities.invokeLater(new SwingThreadRunnable(myLoopCounter));
    }
  }

  private void dumpThreads() {
    System.out.println("EDT is not responding");
    File f = new File(myLogDir, "threadDump-" + myDateFormat.format(new Date()) + ".txt");
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

  private void dumpThreadsToFile(final OutputStreamWriter f) {
    boolean dumpSuccessful = false;
    if (myDumpAllThreadsMethod != null) {
        try {
          ThreadInfo[] threads = (ThreadInfo[])myDumpAllThreadsMethod.invoke(myThreadMXBean, false, false);
          for(ThreadInfo info: threads) {
            dumpCallStack(info, f);
          }
          dumpSuccessful = true;
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
        catch (InvocationTargetException e) {
          e.printStackTrace();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
    }
  }

  private static void dumpCallStack(final ThreadInfo info, final OutputStreamWriter f) throws IOException {
    f.write("\"" + info.getThreadName() + "\"\n");
    StackTraceElement[] stackTraceElements = info.getStackTrace();
    for(StackTraceElement element: stackTraceElements) {
      f.write("\tat " + element.toString() + "\n");
    }
    f.write("\n");
  }

  private class SwingThreadRunnable implements Runnable {
    private int myCount;

    private SwingThreadRunnable(final int count) {
      myCount = count;
    }

    public void run() {
      mySwingThreadCounter = myCount;
    }
  }
}
