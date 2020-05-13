// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.application.options.RegistryManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
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
import java.lang.management.ThreadInfo;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PerformanceWatcher implements Disposable {
  private static final Logger LOG = Logger.getInstance(PerformanceWatcher.class);
  private static final int TOLERABLE_LATENCY = 100;
  private static final String THREAD_DUMPS_PREFIX = "threadDumps-";
  static final String DUMP_PREFIX = "threadDump-";
  private static final String DURATION_FILE_NAME = ".duration";
  private ScheduledFuture<?> myThread;
  private final File myLogDir = new File(PathManager.getLogPath());

  private volatile ApdexData mySwingApdex = ApdexData.EMPTY;
  private volatile ApdexData myGeneralApdex = ApdexData.EMPTY;
  private volatile long myLastSampling = System.nanoTime();

  private int myActiveEvents;

  private static final long ourIdeStart = System.currentTimeMillis();

  private final ScheduledExecutorService myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("EDT Performance Checker", 1);
  private FreezeCheckerTask myCurrentEDTEventChecker;

  private static final boolean SHOULD_WATCH = shouldWatch();

  public static @NotNull PerformanceWatcher getInstance() {
    LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred();
    return ServiceManager.getService(PerformanceWatcher.class);
  }

  public PerformanceWatcher() {
    if (!shouldWatch()) return;

    AppScheduledExecutorService service = (AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService();
    service.setNewThreadListener(new Consumer<Thread>() {
      private final int ourReasonableThreadPoolSize = RegistryManager.getInstance().intValue("core.pooled.threads");

      @Override
      public void accept(Thread thread) {
        if (service.getBackendPoolExecutorSize() > ourReasonableThreadPoolSize
            && ApplicationInfoImpl.getShadowInstance().isEAP()) {
          File file = dumpThreads("newPooledThread/", true);
          LOG.info("Not enough pooled threads" + (file != null ? "; dumped threads into file '" + file.getPath() + "'" : ""));
        }
      }
    });

    for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
      if ("Code Cache".equals(bean.getName())) {
        watchCodeCache(bean);
        break;
      }
    }

    reportCrashesIfAny();
    cleanOldFiles(myLogDir, 0);

    myThread =
      myExecutor.scheduleWithFixedDelay(this::samplePerformance, getSamplingInterval(), getSamplingInterval(), TimeUnit.MILLISECONDS);
  }

  private static void reportCrashesIfAny() {
    try {
      File systemDir = new File(PathManager.getSystemPath());
      File appInfoFile = new File(systemDir, IdeaFreezeReporter.APPINFO_FILE_NAME);
      if (appInfoFile.isFile()) {
        File[] crashFiles = new File(SystemProperties.getUserHome())
          .listFiles(file -> file.getName().startsWith("java_error_in") && !file.getName().endsWith("hprof") && file.isFile());
        for (File file : crashFiles) {
          if (file.lastModified() > appInfoFile.lastModified()) {
            if (file.length() > 5 * FileUtilRt.MEGABYTE) {
              LOG.info("Crash file " + file + " is too big to report");
              break;
            }
            String content = FileUtil.loadFile(file);
            Attachment attachment = new Attachment("crash.txt", content);
            attachment.setIncluded(true);
            String message = StringUtil.substringBefore(content, "---------------  P R O C E S S  ---------------");
            IdeaLoggingEvent event = LogMessage.createEvent(new JBRCrash(), message, attachment);
            IdeaFreezeReporter.setAppInfo(event, FileUtil.loadFile(appInfoFile));
            IdeaFreezeReporter.report(event);
            break;
          }
        }
      }
      IdeaFreezeReporter.saveAppInfo(systemDir, true);
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  private static @NotNull IdePerformanceListener getPublisher() {
    return ApplicationManager.getApplication().getMessageBus().syncPublisher(IdePerformanceListener.TOPIC);
  }

  private static int getMaxAttempts() {
    return RegistryManager.getInstance().intValue("performance.watcher.unresponsive.max.attempts.before.log");
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

  public void processUnfinishedFreeze(@NotNull BiConsumer<File, Integer> consumer) {
    File[] files = myLogDir.listFiles();
    if (files != null) {
      Arrays.stream(files)
        .filter(file -> file.getName().startsWith(THREAD_DUMPS_PREFIX))
        .filter(file -> Files.exists(file.toPath().resolve(DURATION_FILE_NAME)))
        .findFirst().ifPresent(f -> {
        File marker = new File(f, DURATION_FILE_NAME);
        try {
          String s = FileUtil.loadFile(marker);
          cleanup(f);
          consumer.accept(f, Integer.parseInt(s));
        }
        catch (Exception ignored) {
        }
      });
    }
  }

  private static void cleanOldFiles(File dir, final int level) {
    File[] children = dir.listFiles((dir1, name) -> level > 0 || name.startsWith(THREAD_DUMPS_PREFIX));
    if (children == null) return;

    Arrays.sort(children);
    for (int i = 0; i < children.length; i++) {
      File child = children[i];
      if (i < children.length - 100 || ageInDays(child) > 10) {
        FileUtil.delete(child);
      }
      else if (level < 3) {
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
    myExecutor.shutdownNow();
  }

  private static boolean shouldWatch() {
    return !ApplicationManager.getApplication().isHeadlessEnvironment() &&
           getUnresponsiveInterval() != 0 &&
           getMaxAttempts() != 0;
  }

  private void samplePerformance() {
    long current = System.nanoTime();
    long diffMs = TimeUnit.NANOSECONDS.toMillis(current - myLastSampling) - getSamplingInterval();
    myLastSampling = current;

    // an unexpected delay of 3 seconds is considered as several delays: of 3, 2 and 1 seconds, because otherwise
    // this background thread would be sampled 3 times.
    while (diffMs >= 0) {
      myGeneralApdex = myGeneralApdex.withEvent(TOLERABLE_LATENCY, diffMs);
      diffMs -= getSamplingInterval();
    }

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current);
      mySwingApdex = mySwingApdex.withEvent(TOLERABLE_LATENCY, latencyMs);
      if (ApplicationManager.getApplication().isDisposed()) return;
      getPublisher().uiResponded(latencyMs);
    });
  }

  public static @NotNull String printStacktrace(@NotNull String headerMsg, @NotNull Thread thread, StackTraceElement @NotNull [] stackTrace) {
    @SuppressWarnings("NonConstantStringShouldBeStringBuffer")
    StringBuilder trace = new StringBuilder(
      headerMsg + thread + " (" + (thread.isAlive() ? "alive" : "dead") + ") " + thread.getState() + "\n--- its stacktrace:\n");
    for (final StackTraceElement stackTraceElement : stackTrace) {
      trace.append(" at ").append(stackTraceElement).append("\n");
    }
    trace.append("---\n");
    return trace.toString();
  }

  private static int getSamplingInterval() {
    return RegistryManager.getInstance().intValue("performance.watcher.sampling.interval.ms");
  }

  static int getDumpInterval() {
    return getSamplingInterval() * getMaxAttempts();
  }

  static int getUnresponsiveInterval() {
    return RegistryManager.getInstance().intValue("performance.watcher.unresponsive.interval.ms");
  }

  static int getMaxDumpDuration() {
    return RegistryManager.getInstance().intValue("performance.watcher.dump.duration.s") * 1000;
  }

  private static String buildName() {
    return ApplicationInfo.getInstance().getBuild().asString();
  }

  private static String formatTime(long timeMs) {
    return new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(timeMs));
  }

  private static void cleanup(File dir) {
    FileUtil.delete(new File(dir, DURATION_FILE_NAME));
  }

  public void edtEventStarted() {
    long start = System.nanoTime();
    myActiveEvents++;
    if (SHOULD_WATCH) {
      finishTracking();
      startTracking(start);
    }
  }

  public void edtEventFinished() {
    myActiveEvents--;
    finishTracking();
    if (SHOULD_WATCH && myActiveEvents > 0) {
      startTracking(System.nanoTime());
    }
  }

  private void startTracking(long start) {
    int delay = getUnresponsiveInterval();
    if (delay > 0) {
      myCurrentEDTEventChecker = new FreezeCheckerTask(start, delay);
    }
  }

  private void finishTracking() {
    FreezeCheckerTask currentChecker = myCurrentEDTEventChecker;
    if (currentChecker != null) {
      currentChecker.stop();
      myCurrentEDTEventChecker = null;
    }
  }

  public @Nullable File dumpThreads(@NotNull String pathPrefix, boolean millis) {
    return dumpThreads(pathPrefix, millis, ThreadDumper.getThreadInfos(), null);
  }

  private @Nullable File dumpThreads(@NotNull String pathPrefix, boolean millis, ThreadInfo[] threadInfos, @Nullable FreezeCheckerTask task) {
    if (!shouldWatch()) return null;

    if (!pathPrefix.contains("/")) {
      pathPrefix = THREAD_DUMPS_PREFIX + pathPrefix + "-" + formatTime(ourIdeStart) + "-" + buildName() + "/";
    }
    else if (!pathPrefix.startsWith(THREAD_DUMPS_PREFIX)) {
      pathPrefix = THREAD_DUMPS_PREFIX + pathPrefix;
    }

    long now = System.currentTimeMillis();
    String suffix = millis ? "-" + now : "";
    File file = new File(myLogDir, pathPrefix + DUMP_PREFIX + formatTime(now) + suffix + ".txt");

    File dir = file.getParentFile();
    if (!(dir.isDirectory() || dir.mkdirs())) {
      return null;
    }

    checkMemoryUsage(file);

    ThreadDump threadDump = ThreadDumper.getThreadDumpInfo(threadInfos);
    try {
      FileUtil.writeToFile(file, threadDump.getRawDump());
      if (task != null) {
        FileUtil.writeToFile(new File(dir, DURATION_FILE_NAME),
                             String.valueOf(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - task.myFreezeStart)));

        getPublisher().dumpedThreads(file, threadDump);
      }
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

  static List<StackTraceElement> getStacktraceCommonPart(final List<StackTraceElement> commonPart,
                                                         final StackTraceElement[] stackTraceElements) {
    for (int i = 0; i < commonPart.size() && i < stackTraceElements.length; i++) {
      StackTraceElement el1 = commonPart.get(commonPart.size() - i - 1);
      StackTraceElement el2 = stackTraceElements[stackTraceElements.length - i - 1];
      if (!compareStackTraceElements(el1, el2)) {
        return commonPart.subList(commonPart.size() - i, commonPart.size());
      }
    }
    return commonPart;
  }

  // same as java.lang.StackTraceElement.equals, but do not care about the line number
  static boolean compareStackTraceElements(StackTraceElement el1, StackTraceElement el2) {
    if (el1 == el2) {
      return true;
    }
    return el1.getClassName().equals(el2.getClassName()) &&
           Objects.equals(el1.getMethodName(), el2.getMethodName()) &&
           Objects.equals(el1.getFileName(), el2.getFileName());
  }

  public class Snapshot {
    private final ApdexData myStartGeneralSnapshot = myGeneralApdex;
    private final ApdexData myStartSwingSnapshot = mySwingApdex;
    private final long myStartMillis = System.currentTimeMillis();

    private Snapshot() {
    }

    public void logResponsivenessSinceCreation(@NonNls @NotNull String activityName) {
      LOG.info(activityName + " took " + (System.currentTimeMillis() - myStartMillis) + "ms" +
               "; general responsiveness: " + myGeneralApdex.summarizePerformanceSince(myStartGeneralSnapshot) +
               "; EDT responsiveness: " + mySwingApdex.summarizePerformanceSince(myStartSwingSnapshot));
    }
  }

  public static @NotNull Snapshot takeSnapshot() {
    return getInstance().new Snapshot();
  }

  ScheduledExecutorService getExecutor() {
    return myExecutor;
  }

  private enum CheckerState {
    CHECKING, FREEZE, FINISHED
  }

  private class FreezeCheckerTask {
    private final AtomicReference<CheckerState> myState = new AtomicReference<>(CheckerState.CHECKING);
    private final Future<?> myFuture;
    private final long myFreezeStart;
    private String myFreezeFolder;
    private boolean myFreezeDuringStartup;
    private volatile SamplingTask myDumpTask;

    FreezeCheckerTask(long start, int delay) {
      myFuture = !myExecutor.isShutdown() ? myExecutor.schedule(this::edtFrozen, delay, TimeUnit.MILLISECONDS) : null;
      myFreezeStart = start;
    }

    void stop() {
      if (myFuture == null) return;
      myFuture.cancel(false);
      if (myState.getAndSet(CheckerState.FINISHED) == CheckerState.FREEZE) {
        long end = System.nanoTime();
        stopDumping(); // stop sampling as early as possible
        try {
          myExecutor.submit(() -> edtResponds(end)).get();
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      }
    }

    private void edtFrozen() {
      myFreezeFolder = THREAD_DUMPS_PREFIX +
                       (myFreezeDuringStartup ? "freeze-startup-" : "freeze-") +
                       formatTime(System.currentTimeMillis()) + "-" + buildName();
      if (myState.compareAndSet(CheckerState.CHECKING, CheckerState.FREEZE)) {
        //TODO always true for some reason
        //myFreezeDuringStartup = !LoadingState.INDEXING_FINISHED.isOccurred();
        getPublisher().uiFreezeStarted();
        myDumpTask = new SamplingTask(getDumpInterval(), getMaxDumpDuration()) {
          @Override
          protected void dumpedThreads(ThreadInfo[] infos) {
            if (myState.get() == CheckerState.FINISHED) {
              stop();
            }
            else {
              dumpThreads(myFreezeFolder + "/", false, infos, FreezeCheckerTask.this);
            }
          }
        };
      }
    }

    private void edtResponds(long current) {
      stopDumping();

      long durationMs = TimeUnit.NANOSECONDS.toMillis(current - myFreezeStart);
      File dir = new File(myLogDir, myFreezeFolder);
      File reportDir = null;
      if (dir.exists()) {
        cleanup(dir);
        reportDir = new File(myLogDir, dir.getName() + getFreezePlaceSuffix() + "-" + TimeUnit.MILLISECONDS.toSeconds(durationMs) + "sec");
        if (!dir.renameTo(reportDir)) {
          reportDir = dir;
        }
        String message = "UI was frozen for " + durationMs + "ms, details saved to " + reportDir;
        if (PluginManagerCore.isRunningFromSources()) {
          LOG.info(message);
        }
        else {
          LOG.warn(message);
        }
      }
      getPublisher().uiFreezeFinished(durationMs, reportDir);
    }

    private void stopDumping() {
      SamplingTask task = myDumpTask;
      if (task != null) {
        task.stop();
      }
    }

    private String getFreezePlaceSuffix() {
      List<StackTraceElement> stacktraceCommonPart = null;
      for (ThreadInfo[] info : myDumpTask.getThreadInfos()) {
        ThreadInfo edt = ContainerUtil.find(info, ThreadDumper::isEDT);
        if (edt != null) {
          StackTraceElement[] edtStack = edt.getStackTrace();
          if (edtStack != null) {
            if (stacktraceCommonPart == null) {
              stacktraceCommonPart = ContainerUtil.newArrayList(edtStack);
            }
            else {
              stacktraceCommonPart = getStacktraceCommonPart(stacktraceCommonPart, edtStack);
            }
          }
        }
      }

      if (!ContainerUtil.isEmpty(stacktraceCommonPart)) {
        StackTraceElement element = stacktraceCommonPart.get(0);
        return "-" + StringUtil.getShortName(element.getClassName()) + "." + element.getMethodName();
      }
      return "";
    }
  }
}
