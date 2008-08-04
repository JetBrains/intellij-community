package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.io.FileUtil;
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
  private Semaphore myShutdownSemaphore = new Semaphore(1);
  private ThreadMXBean myThreadMXBean;
  private Method myDumpAllThreadsMethod;
  private DateFormat myDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
  private DateFormat myPrintDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
  private File myLogDir;
  private int myUnresponsiveDuration = 0;
  private File myCurHangLogDir;
  private List<StackTraceElement> myStacktraceCommonPart;

  @NotNull
  public String getComponentName() {
    return "PerformanceWatcher";
  }

  public void initComponent() {
    if (shallNotWatch()) return;

    deleteOldThreadDumps();

    myLogDir = new File(PathManager.getSystemPath() + "/log/threadDumps-" + myDateFormat.format(new Date()));
    myLogDir.mkdirs();
    myCurHangLogDir = myLogDir;
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

  private static void deleteOldThreadDumps() {
    File allLogsDir = new File(PathManager.getSystemPath(), "log");
    final String[] dirs = allLogsDir.list(new FilenameFilter() {
      public boolean accept(final File dir, final String name) {
        return name.startsWith("threadDumps-");
      }
    });
    Arrays.sort(dirs);
    for (int i = 0; i < dirs.length - 10; i++) {
      FileUtil.delete(new File(allLogsDir, dirs [i]));
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

  private static boolean shallNotWatch() {
    return ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment();
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
        if (myUnresponsiveDuration == 0) {
          System.out.println("EDT is not responding at " + myPrintDateFormat.format(new Date()));
          myCurHangLogDir = new File(myLogDir, myDateFormat.format(new Date()));
          myCurHangLogDir.mkdirs();
        }
        myUnresponsiveDuration++;
        dumpThreads();
      }
      else if (myUnresponsiveDuration > 0) {
        System.out.println("EDT was unresponsive for " + myUnresponsiveDuration + " seconds");
        myCurHangLogDir.renameTo(new File(myLogDir, getLogDirForHang()));
        myUnresponsiveDuration = 0;
        myCurHangLogDir = myLogDir;

        myStacktraceCommonPart = null;
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
      name.append("-").append(element.getClassName()).append(".").append(element.getMethodName());
    }
    return name.toString();
  }

  private void dumpThreads() {
    File f = new File(myCurHangLogDir, "threadDump-" + myDateFormat.format(new Date()) + ".txt");
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

  private static void dumpCallStack(final ThreadInfo info, final OutputStreamWriter f,
                                    final StackTraceElement[] stackTraceElements) throws IOException {
    f.write("\"" + info.getThreadName() + "\"\n");
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
