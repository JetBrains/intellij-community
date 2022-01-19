// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.application.options.RegistryManager;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ThreadInfo;
import java.nio.file.Files;
import java.nio.file.Path;
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

public final class PerformanceWatcherImpl extends PerformanceWatcher {
  private static final Logger LOG = Logger.getInstance(PerformanceWatcherImpl.class);

  private static final int TOLERABLE_LATENCY = 100;
  private static final String THREAD_DUMPS_PREFIX = "threadDumps-";

  private static final String DURATION_FILE_NAME = ".duration";
  private static final String PID_FILE_NAME = ".pid";
  private final File myLogDir = new File(PathManager.getLogPath());

  private volatile ApdexData mySwingApdex = ApdexData.EMPTY;
  private volatile ApdexData myGeneralApdex = ApdexData.EMPTY;
  private volatile long myLastSampling = System.nanoTime();

  private int myActiveEvents;

  private static final long ourIdeStart = System.currentTimeMillis();

  private final ScheduledExecutorService myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("EDT Performance Checker", 1);
  private @Nullable ScheduledFuture<?> myThread;
  private @Nullable FreezeCheckerTask myCurrentEDTEventChecker;

  private final JitWatcher myJitWatcher = new JitWatcher();

  private final @NotNull RegistryValue mySamplingInterval;
  private final @NotNull RegistryValue myMaxAttemptsCount;
  private final @NotNull RegistryValue myUnresponsiveInterval;
  private final @NotNull RegistryValue myMaxDumpDuration;

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  private PerformanceWatcherImpl() {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      throw ExtensionNotApplicableException.create();
    }

    RegistryManager registryManager = application.getService(RegistryManager.class);
    mySamplingInterval = registryManager.get("performance.watcher.sampling.interval.ms");
    myMaxAttemptsCount = registryManager.get("performance.watcher.unresponsive.max.attempts.before.log");
    myUnresponsiveInterval = registryManager.get("performance.watcher.unresponsive.interval.ms");
    myMaxDumpDuration = registryManager.get("performance.watcher.dump.duration.s");

    if (application.isHeadlessEnvironment()) {
      return;
    }

    RegistryValueListener cancelingListener = new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        int samplingIntervalMs = getUnresponsiveInterval() > 0 && getMaxAttemptsCount() > 0 ?
                                 getSamplingInterval() :
                                 0;

        if (samplingIntervalMs <= 0) {
          cancelThread();
          myThread = null;
        }
        else if (mySamplingInterval == value) {
          cancelThread();
          myThread = myExecutor.scheduleWithFixedDelay(() -> samplePerformance(samplingIntervalMs),
                                                       samplingIntervalMs,
                                                       samplingIntervalMs,
                                                       TimeUnit.MILLISECONDS);
        }
      }
    };

    for (RegistryValue value : List.of(mySamplingInterval, myMaxAttemptsCount, myUnresponsiveInterval)) {
      value.addListener(cancelingListener, this);
    }

    RegistryValue ourReasonableThreadPoolSize = registryManager.get("reasonable.application.thread.pool.size");
    AppScheduledExecutorService service = (AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService();
    service.setNewThreadListener((thread, runnable) -> {
      if (service.getBackendPoolExecutorSize() > ourReasonableThreadPoolSize.asInteger() &&
          ApplicationInfoImpl.getShadowInstance().isEAP()) {
        String message = "Too many pooled threads created (" + service.getBackendPoolExecutorSize() + " > " + ourReasonableThreadPoolSize + ")";
        File file = doDumpThreads("newPooledThread/", true, message);
        LOG.info(message + (file == null ? "" : "; thread dump is saved to '" + file.getPath() + "'"));
      }
    });

    reportCrashesIfAny();
    cleanOldFiles(myLogDir, 0);

    cancelingListener.afterValueChanged(mySamplingInterval);
    ourInstance = this;
  }


  private static void reportCrashesIfAny() {
    Path systemDir = Path.of(PathManager.getSystemPath());
    try {
      Path appInfoFile = systemDir.resolve(IdeaFreezeReporter.APPINFO_FILE_NAME);
      Path pidFile = systemDir.resolve(PID_FILE_NAME);
      // TODO: check jre in app info, not the current
      // Only report if on JetBrains jre
      if (SystemInfo.isJetBrainsJvm && Files.isRegularFile(appInfoFile) && Files.isRegularFile(pidFile)) {
        String pid = Files.readString(pidFile);
        File[] crashFiles = new File(SystemProperties.getUserHome()).listFiles(file -> {
          return file.getName().startsWith("java_error_in") && file.getName().endsWith(pid + ".log") && file.isFile();
        });
        if (crashFiles != null) {
          long appInfoFileLastModified = Files.getLastModifiedTime(appInfoFile).toMillis();
          for (File file : crashFiles) {
            if (file.lastModified() > appInfoFileLastModified) {
              if (file.length() > 5 * FileUtilRt.MEGABYTE) {
                LOG.info("Crash file " + file + " is too big to report");
                break;
              }
              String content = FileUtil.loadFile(file);
              // TODO: maybe we need to notify the user
              if (content.contains("fuck_the_regulations")) {
                break;
              }
              Attachment attachment = new Attachment("crash.txt", content);
              attachment.setIncluded(true);

              // include plugins list
              String plugins = StreamEx.of(PluginManagerCore.getLoadedPlugins())
                .filter(d -> d.isEnabled() && !d.isBundled())
                .map(PluginInfoDetectorKt::getPluginInfoByDescriptor)
                .filter(PluginInfo::isSafeToReport)
                .map(i -> i.getId() + " (" + i.getVersion() + ")")
                .joining("\n", "Extra plugins:\n", "");
              Attachment pluginsAttachment = new Attachment("plugins.txt", plugins);
              attachment.setIncluded(true);

              Attachment[] attachments = new Attachment[]{attachment, pluginsAttachment};

              // look for extended crash logs
              File extraLog = findExtraLogFile(pid, appInfoFileLastModified);
              if (extraLog != null) {
                String jbrErrContent = FileUtil.loadFile(extraLog);
                // Detect crashes caused by OOME
                if (jbrErrContent.contains("java.lang.OutOfMemoryError: Java heap space")) {
                  LowMemoryNotifier.showNotification(VMOptions.MemoryKind.HEAP, true);
                }
                Attachment extraAttachment = new Attachment("jbr_err.txt", jbrErrContent);
                extraAttachment.setIncluded(true);
                attachments = ArrayUtil.append(attachments, extraAttachment);
              }

              String message = StringUtil.substringBefore(content, "---------------  P R O C E S S  ---------------");
              IdeaLoggingEvent event = LogMessage.createEvent(new JBRCrash(), message, attachments);
              IdeaFreezeReporter.setAppInfo(event, Files.readString(appInfoFile));
              IdeaFreezeReporter.report(event);
              LifecycleUsageTriggerCollector.onCrashDetected();
              break;
            }
          }
        }
      }

      IdeaFreezeReporter.saveAppInfo(appInfoFile, true);
      Files.createDirectories(pidFile.getParent());
      Files.writeString(pidFile, OSProcessUtil.getApplicationPid());
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @Nullable
  private static File findExtraLogFile(String pid, long lastModified) {
    if (!SystemInfo.isMac) {
      return null;
    }
    String logFileName = "jbr_err_pid" + pid + ".log";
    List<File> candidates = List.of(new File(SystemProperties.getUserHome(), logFileName), new File(logFileName));
    return ContainerUtil.find(candidates, file -> file.isFile() && file.lastModified() > lastModified);
  }

  private static @Nullable IdePerformanceListener getPublisher() {
    Application application = ApplicationManager.getApplication();
    return application != null && !application.isDisposed() ?
           application.getMessageBus().syncPublisher(IdePerformanceListener.TOPIC) :
           null;
  }

  @Override
  public void processUnfinishedFreeze(@NotNull BiConsumer<? super File, ? super Integer> consumer) {
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

  private void cancelThread() {
    if (myThread != null) {
      myThread.cancel(true);
    }
  }

  @Override
  public void dispose() {
    cancelThread();
    myExecutor.shutdownNow();
  }

  private void samplePerformance(long samplingIntervalMs) {
    long current = System.nanoTime();
    long diffMs = TimeUnit.NANOSECONDS.toMillis(current - myLastSampling) - samplingIntervalMs;
    myLastSampling = current;

    // an unexpected delay of 3 seconds is considered as several delays: of 3, 2 and 1 seconds, because otherwise
    // this background thread would be sampled 3 times.
    while (diffMs >= 0) {
      //noinspection NonAtomicOperationOnVolatileField
      myGeneralApdex = myGeneralApdex.withEvent(TOLERABLE_LATENCY, diffMs);
      diffMs -= samplingIntervalMs;
    }

    myJitWatcher.checkJitState();

    SwingUtilities.invokeLater(() -> {
      long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current);
      //noinspection NonAtomicOperationOnVolatileField
      mySwingApdex = mySwingApdex.withEvent(TOLERABLE_LATENCY, latencyMs);

      IdePerformanceListener publisher = getPublisher();
      if (publisher != null) {
        publisher.uiResponded(latencyMs);
      }
    });
  }

  private int getSamplingInterval() {
    return mySamplingInterval.asInteger();
  }

  private int getMaxAttemptsCount() {
    return myMaxAttemptsCount.asInteger();
  }

  @Override
  public int getDumpInterval() {
    return getSamplingInterval() * getMaxAttemptsCount();
  }

  @Override
  public int getUnresponsiveInterval() {
    return myUnresponsiveInterval.asInteger();
  }

  @Override
  public int getMaxDumpDuration() {
    return myMaxDumpDuration.asInteger() * 1000;
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

  @Override
  @ApiStatus.Internal
  public void edtEventStarted() {
    long start = System.nanoTime();
    myActiveEvents++;

    if (myThread != null) {
      if (myCurrentEDTEventChecker != null) {
        myCurrentEDTEventChecker.stop();
      }
      myCurrentEDTEventChecker = new FreezeCheckerTask(start);
    }
  }

  @Override
  @ApiStatus.Internal
  public void edtEventFinished() {
    myActiveEvents--;

    if (myThread != null) {
      Objects.requireNonNull(myCurrentEDTEventChecker).stop();
      myCurrentEDTEventChecker = myActiveEvents > 0 ? new FreezeCheckerTask(System.nanoTime()) : null;
    }
  }

  @Override
  public @Nullable File dumpThreads(@NotNull String pathPrefix, boolean appendMillisecondsToFileName) {
    return doDumpThreads(pathPrefix, appendMillisecondsToFileName, "");
  }

  @Nullable
  private File doDumpThreads(@NotNull String pathPrefix, boolean appendMillisecondsToFileName, @NotNull String contentsPrefix) {
    return myThread == null
           ? null
           : dumpThreads(pathPrefix, appendMillisecondsToFileName,
                         contentsPrefix + ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos()).getRawDump());
  }

  private @Nullable File dumpThreads(@NotNull String pathPrefix, boolean appendMillisecondsToFileName, @NotNull String rawDump) {
    if (!pathPrefix.contains("/")) {
      pathPrefix = THREAD_DUMPS_PREFIX + pathPrefix + "-" + formatTime(ourIdeStart) + "-" + buildName() + "/";
    }
    else if (!pathPrefix.startsWith(THREAD_DUMPS_PREFIX)) {
      pathPrefix = THREAD_DUMPS_PREFIX + pathPrefix;
    }

    long now = System.currentTimeMillis();
    String suffix = appendMillisecondsToFileName ? "-" + now : "";
    File file = new File(myLogDir, pathPrefix + DUMP_PREFIX + formatTime(now) + suffix + ".txt");

    File dir = file.getParentFile();
    if (!(dir.isDirectory() || dir.mkdirs())) {
      return null;
    }

    String memoryUsage = getMemoryUsage();
    if (!memoryUsage.isEmpty()) {
      LOG.info(memoryUsage + " while dumping threads to " + file);
    }

    try {
      FileUtil.writeToFile(file, rawDump);
    }
    catch (IOException e) {
      LOG.info("Failed to write the thread dump file: " + e.getMessage());
    }
    return file;
  }

  private @NotNull String getMemoryUsage() {
    Runtime rt = Runtime.getRuntime();
    long maxMemory = rt.maxMemory();
    long usedMemory = rt.totalMemory() - rt.freeMemory();
    long freeMemory = maxMemory - usedMemory;

    String diagnosticInfo = "";

    if (freeMemory < maxMemory / 5) {
      diagnosticInfo = "High memory usage (free " + (freeMemory / 1024 / 1024) + " of " + (maxMemory / 1024 / 1024) + " MB)";
    }

    String jitProblem = getJitProblem();
    if (jitProblem != null) {
      if (!diagnosticInfo.isEmpty()) {
        diagnosticInfo += ", ";
      }
      diagnosticInfo += jitProblem;
    }
    return diagnosticInfo;
  }

  @Override
  public @Nullable String getJitProblem() {
    return myJitWatcher.getJitProblem();
  }

  @NotNull
  static List<StackTraceElement> getStacktraceCommonPart(final @NotNull List<StackTraceElement> commonPart,
                                                         final StackTraceElement @NotNull [] stackTraceElements) {
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

  @Override
  public void clearFreezeStacktraces() {
    if (myCurrentEDTEventChecker != null) {
      myCurrentEDTEventChecker.stopDumping();
    }
  }

  public final class SnapshotImpl implements Snapshot {
    private final ApdexData myStartGeneralSnapshot = myGeneralApdex;
    private final ApdexData myStartSwingSnapshot = mySwingApdex;
    private final long myStartMillis = System.currentTimeMillis();

    private SnapshotImpl() {
    }

    @Override
    public void logResponsivenessSinceCreation(@NonNls @NotNull String activityName) {
      LOG.info(getLogResponsivenessSinceCreationMessage(activityName));
    }

    @Override
    @NotNull
    public String getLogResponsivenessSinceCreationMessage(@NonNls @NotNull String activityName) {
      return activityName + " took " + (System.currentTimeMillis() - myStartMillis) + "ms" +
             "; general responsiveness: " + myGeneralApdex.summarizePerformanceSince(myStartGeneralSnapshot) +
             "; EDT responsiveness: " + mySwingApdex.summarizePerformanceSince(myStartSwingSnapshot);
    }
  }

  @Override
  public ScheduledExecutorService getExecutor() {
    return myExecutor;
  }

  private enum CheckerState {
    CHECKING, FREEZE, FINISHED
  }

  private final class FreezeCheckerTask {

    private final AtomicReference<CheckerState> myState = new AtomicReference<>(CheckerState.CHECKING);
    private final @NotNull Future<?> myFuture;
    private final long myTaskStart;
    private String myFreezeFolder;
    private volatile SamplingTask myDumpTask;

    FreezeCheckerTask(long taskStart) {
      myFuture = myExecutor.schedule(this::edtFrozen,
                                     getUnresponsiveInterval(),
                                     TimeUnit.MILLISECONDS);
      myTaskStart = taskStart;
    }

    private long getDuration(long current,
                             @NotNull TimeUnit unit) {
      return unit.convert(current - myTaskStart, TimeUnit.NANOSECONDS);
    }

    void stop() {
      myFuture.cancel(false);

      if (myState.getAndSet(CheckerState.FINISHED) == CheckerState.FREEZE) {
        long taskStop = System.nanoTime();
        stopDumping(); // stop sampling as early as possible
        try {
          myExecutor.submit(() -> {
            stopDumping();

            long durationMs = getDuration(taskStop, TimeUnit.MILLISECONDS);
            IdePerformanceListener publisher = getPublisher();
            if (publisher != null) {
              publisher.uiFreezeFinished(durationMs, new File(myLogDir, myFreezeFolder));
            }
            File reportDir = postProcessReportFolder(durationMs);
            if (publisher != null) {
              publisher.uiFreezeRecorded(durationMs, reportDir);
            }
          }).get();
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      }
    }

    private void edtFrozen() {
      myFreezeFolder = THREAD_DUMPS_PREFIX +
                       "freeze-" +
                       formatTime(System.currentTimeMillis()) + "-" + buildName();
      if (myState.compareAndSet(CheckerState.CHECKING, CheckerState.FREEZE)) {
        //TODO always true for some reason
        //myFreezeDuringStartup = !LoadingState.INDEXING_FINISHED.isOccurred();
        File reportDir = new File(myLogDir, myFreezeFolder);
        reportDir.mkdirs();

        IdePerformanceListener publisher = getPublisher();
        if (publisher == null) {
          return;
        }
        publisher.uiFreezeStarted(reportDir);

        myDumpTask = new SamplingTask(getDumpInterval(), getMaxDumpDuration()) {

          @Override
          protected void dumpedThreads(@NotNull ThreadDump threadDump) {
            if (myState.get() == CheckerState.FINISHED) {
              stop();
            }
            else {
              File file = dumpThreads(myFreezeFolder + "/",
                                      false,
                                      threadDump.getRawDump());
              if (file != null) {
                try {
                  long duration = getDuration(System.nanoTime(), TimeUnit.SECONDS);
                  FileUtil.writeToFile(new File(file.getParentFile(), DURATION_FILE_NAME),
                                       Long.toString(duration));
                  publisher.dumpedThreads(file, threadDump);
                }
                catch (IOException e) {
                  LOG.info("Failed to write the duration file: " + e.getMessage());
                }
              }
            }
          }
        };
      }
    }

    private @Nullable File postProcessReportFolder(long durationMs) {
      File dir = new File(myLogDir, myFreezeFolder);
      File reportDir = null;
      if (dir.exists()) {
        cleanup(dir);
        reportDir = new File(myLogDir, dir.getName() + getFreezePlaceSuffix() + "-" + TimeUnit.MILLISECONDS.toSeconds(durationMs) + "sec");
        if (!dir.renameTo(reportDir)) {
          LOG.warn("Unable to create freeze folder " + reportDir);
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
      return reportDir;
    }

    void stopDumping() {
      SamplingTask task = myDumpTask;
      if (task != null) {
        task.stop();
        myDumpTask = null;
      }
    }

    private String getFreezePlaceSuffix() {
      List<StackTraceElement> stacktraceCommonPart = null;
      SamplingTask task = myDumpTask;
      if (task == null) {
        return "";
      }
      for (ThreadInfo[] info : task.getThreadInfos()) {
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
        return "-" +
               FileUtil.sanitizeFileName(StringUtil.getShortName(element.getClassName())) +
               "." +
               FileUtil.sanitizeFileName(element.getMethodName());
      }
      return "";
    }
  }

  @Override
  protected Snapshot newSnapshot() {
    return new SnapshotImpl();
  }
}
