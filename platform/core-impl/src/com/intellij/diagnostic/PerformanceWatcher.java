/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ThreadMXBean;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yole
 */
public class PerformanceWatcher implements Disposable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.diagnostic.PerformanceWatcher");
  private static final int TOLERABLE_LATENCY = 100;
  private static final String THREAD_DUMPS_PREFIX = "threadDumps-";
  private final ScheduledFuture<?> myThread;
  private final ThreadMXBean myThreadMXBean;
  private final DateFormat myDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
  private final File myLogDir = new File(PathManager.getLogPath());
  private List<StackTraceElement> myStacktraceCommonPart;
  private final IdePerformanceListener myPublisher;

  private volatile ApdexData mySwingApdex = ApdexData.EMPTY;
  private volatile ApdexData myGeneralApdex = ApdexData.EMPTY;
  private volatile long myLastSampling = System.currentTimeMillis();
  private long myLastDumpTime;
  private long myFreezeStart;
  private final AtomicInteger myEdtRequestsQueued = new AtomicInteger(0);

  private static final long ourIdeStart = System.currentTimeMillis();
  private long myLastEdtAlive = System.currentTimeMillis();

  public static PerformanceWatcher getInstance() {
    return ApplicationManager.getApplication().getComponent(PerformanceWatcher.class);
  }

  public PerformanceWatcher() {
    myPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(IdePerformanceListener.TOPIC);
    myThreadMXBean = ManagementFactory.getThreadMXBean();
    myThread = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> samplePerformance(), getSamplingInterval(), getSamplingInterval(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void initComponent() {
    if (shouldWatch()) {
      final AppScheduledExecutorService service = (AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService();
      service.setNewThreadListener(new Consumer<Thread>() {
        private final int ourReasonableThreadPoolSize = Registry.intValue("core.pooled.threads");

        @Override
        public void consume(Thread thread) {
          if (service.getBackendPoolExecutorSize() > ourReasonableThreadPoolSize
              && ApplicationInfoImpl.getShadowInstance().isEAP()) {
            File file = dumpThreads("newPooledThread/", true);
            LOG.info("Not enough pooled threads" + (file != null ? "; dumped threads into file '" + file.getPath() + "'" : ""));
          }
        }
      });

      ApplicationManager.getApplication().executeOnPooledThread(() -> cleanOldFiles(myLogDir, 0));

      for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
        if ("Code Cache".equals(bean.getName())) {
          watchCodeCache(bean);
          break;
        }
      }
    }
  }

  private static int getMaxAttempts() {
    return Registry.intValue("performance.watcher.unresponsive.max.attempts.before.log");
  }

  private void watchCodeCache(final MemoryPoolMXBean bean) {
    final long threshold = bean.getUsage().getMax() - 5 * 1024 * 1024;
    if (!bean.isUsageThresholdSupported() || threshold <= 0) return;

    bean.setUsageThreshold(threshold);
    final NotificationEmitter emitter = (NotificationEmitter)ManagementFactory.getMemoryMXBean();
    emitter.addNotificationListener(new NotificationListener() {
      @Override
      public void handleNotification(Notification n, Object hb) {
        if (bean.getUsage().getUsed() > threshold) {
          LOG.info("Code Cache is almost full");
          dumpThreads("codeCacheFull", true);
          try {
            emitter.removeNotificationListener(this);
          }
          catch (ListenerNotFoundException e) {
            LOG.error(e);
          }
        }
      }
    }, null, null);
  }

  private static void cleanOldFiles(File dir, final int level) {
    File[] children = dir.listFiles((dir1, name) -> level > 0 || name.startsWith(THREAD_DUMPS_PREFIX));
    if (children == null) return;

    Arrays.sort(children);
    for (int i = 0; i < children.length; i++) {
      File child = children[i];
      if (i < children.length - 100 || ageInDays(child) > 10) {
        FileUtil.delete(child);
      } else if (level < 3) {
        cleanOldFiles(child, level + 1);
      }
    }
  }

  private static long ageInDays(File file) {
    return TimeUnit.DAYS.convert(System.currentTimeMillis() - file.lastModified(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void dispose() {
    if (myThread != null) {
      myThread.cancel(true);
    }
  }

  private static boolean shouldWatch() {
    return !ApplicationManager.getApplication().isHeadlessEnvironment() &&
           Registry.intValue("performance.watcher.unresponsive.interval.ms") != 0 &&
           getMaxAttempts() != 0;
  }

  private void samplePerformance() {
    long millis = System.currentTimeMillis();
    long diff = millis - myLastSampling - getSamplingInterval();
    myLastSampling = millis;

    // an unexpected delay of 3 seconds is considered as several delays: of 3, 2 and 1 seconds, because otherwise
    // this background thread would be sampled 3 times.
    while (diff >= 0) {
      myGeneralApdex = myGeneralApdex.withEvent(TOLERABLE_LATENCY, diff);
      diff -= getSamplingInterval();
    }

    int edtRequests = myEdtRequestsQueued.get();
    if (edtRequests >= getMaxAttempts()) {
      edtFrozen(millis);
    }
    else if (edtRequests == 0) {
      edtResponds(millis);
    }

    myEdtRequestsQueued.incrementAndGet();
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new SwingThreadRunnable(millis));
  }

  private static int getSamplingInterval() {
    return Registry.intValue("performance.watcher.sampling.interval.ms");
  }

  private void edtFrozen(long currentMillis) {
    if (currentMillis - myLastDumpTime >= Registry.intValue("performance.watcher.unresponsive.interval.ms")) {
      myLastDumpTime = currentMillis;
      if (myFreezeStart == 0) {
        myFreezeStart = myLastEdtAlive;
        myPublisher.uiFreezeStarted();
      }
      dumpThreads(getFreezeFolderName(myFreezeStart) + "/", false);
    }
  }

  @NotNull
  private String getFreezeFolderName(long freezeStartMs) {
    return THREAD_DUMPS_PREFIX + "freeze-" + formatTime(freezeStartMs) + "-" + buildName();
  }

  private static String buildName() {
    return ApplicationInfo.getInstance().getBuild().asString();
  }

  private String formatTime(long timeMs) {
    return myDateFormat.format(new Date(timeMs));
  }

  private void edtResponds(long currentMillis) {
    if (myFreezeStart != 0) {
      int unresponsiveDuration = (int)(currentMillis - myFreezeStart) / 1000;
      File dir = new File(myLogDir, getFreezeFolderName(myFreezeStart));
      if (dir.exists()) {
        //noinspection ResultOfMethodCallIgnored
        dir.renameTo(new File(myLogDir, dir.getName() + "-" + unresponsiveDuration + "sec" + getFreezePlaceSuffix()));
      }
      myPublisher.uiFreezeFinished(unresponsiveDuration);
      myFreezeStart = 0;

      myStacktraceCommonPart = null;
    }
  }

  private String getFreezePlaceSuffix() {
    if (myStacktraceCommonPart != null && !myStacktraceCommonPart.isEmpty()) {
      final StackTraceElement element = myStacktraceCommonPart.get(0);
      return "-" + StringUtil.getShortName(element.getClassName()) + "." + element.getMethodName();
    }
    return "";
  }

  @Nullable
  public File dumpThreads(@NotNull String pathPrefix, boolean millis) {
    if (!shouldWatch()) return null;

    if (!pathPrefix.contains("/")) {
      pathPrefix = THREAD_DUMPS_PREFIX + pathPrefix + "-" + formatTime(ourIdeStart) + "-" + buildName() + "/";
    }
    else if (!pathPrefix.startsWith(THREAD_DUMPS_PREFIX)) {
      pathPrefix = THREAD_DUMPS_PREFIX + pathPrefix;
    }

    long now = System.currentTimeMillis();
    String suffix = millis ? "-" + now : "";
    File file = new File(myLogDir, pathPrefix + "threadDump-" + formatTime(now) + suffix + ".txt");

    File dir = file.getParentFile();
    if (!(dir.isDirectory() || dir.mkdirs())) {
      return null;
    }

    checkMemoryUsage(file);

    ThreadDump threadDump = ThreadDumper.getThreadDumpInfo(myThreadMXBean);
    try {
      FileUtil.writeToFile(file, threadDump.getRawDump());
      StackTraceElement[] edtStack = threadDump.getEDTStackTrace();
      if (edtStack != null) {
        if (myStacktraceCommonPart == null) {
          myStacktraceCommonPart = ContainerUtil.newArrayList(edtStack);
        }
        else {
          updateStacktraceCommonPart(edtStack);
        }
      }

      myPublisher.dumpedThreads(file, threadDump);
    }
    catch (IOException e) {
      LOG.info("failed to write thread dump file: " + e.getMessage());
    }
    return file;
  }

  private static void checkMemoryUsage(File file) {
    Runtime rt = Runtime.getRuntime();
    long maxMemory = rt.maxMemory();
    long usedMemory = rt.totalMemory() - rt.freeMemory();
    long freeMemory = maxMemory - usedMemory;
    if (freeMemory < maxMemory / 5) {
      LOG.info("High memory usage (free " + freeMemory / 1024 / 1024 +
               " of " + maxMemory / 1024 / 1024 +
               " MB) while dumping threads to " + file);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void dumpThreadsToConsole(String message) {
    System.err.println(message);
    System.err.println(ThreadDumper.dumpThreadsToString());
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
    private final long myCreationMillis;

    SwingThreadRunnable(long creationMillis) {
      myCreationMillis = creationMillis;
    }

    @Override
    public void run() {
      myEdtRequestsQueued.decrementAndGet();
      myLastEdtAlive = System.currentTimeMillis();
      mySwingApdex = mySwingApdex.withEvent(TOLERABLE_LATENCY, System.currentTimeMillis() - myCreationMillis);
    }
  }

  public class Snapshot {
    private final ApdexData myStartGeneralSnapshot = myGeneralApdex;
    private final ApdexData myStartSwingSnapshot = mySwingApdex;
    private final long myStartMillis = System.currentTimeMillis();

    private Snapshot() {
    }
    public void logResponsivenessSinceCreation(@NotNull String activityName) {
      LOG.info(activityName + " took " + (System.currentTimeMillis() - myStartMillis) + "ms" +
               "; general responsiveness: " + myGeneralApdex.summarizePerformanceSince(myStartGeneralSnapshot) +
               "; EDT responsiveness: " + mySwingApdex.summarizePerformanceSince(myStartSwingSnapshot));
    }

  }

  @NotNull
  public static Snapshot takeSnapshot() {
    return getInstance().new Snapshot();
  }
}
