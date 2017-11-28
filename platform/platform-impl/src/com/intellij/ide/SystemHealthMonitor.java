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
package com.intellij.ide;

import com.android.tools.analytics.UsageTracker;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import com.google.wireless.android.sdk.stats.*;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.google.wireless.android.sdk.stats.UIActionStats.InvocationKind;
import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.IdePerformanceListener;
import com.intellij.diagnostic.ThreadDump;
import com.intellij.diagnostic.VMOptions;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.actions.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.internal.statistic.analytics.StudioCrashDetection;
import com.intellij.jna.JnaLoader;
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationFullContent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.JdkBundle;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SystemHealthMonitor implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(SystemHealthMonitor.class);

  private static final NotificationGroup GROUP = new NotificationGroup("System Health", NotificationDisplayType.STICKY_BALLOON, true);
  private static final String SWITCH_JDK_ACTION = "SwitchBootJdk";
  private static final int LATEST_JDK8_UPDATE = 144;
  private static final String LATEST_JDK_RELEASE = "1.8.0u" + LATEST_JDK8_UPDATE;

  /** Count of action events fired. This is used as a proxy for user initiated activity in the IDE. */
  public static final AtomicLong ourStudioActionCount = new AtomicLong(0);
  private static final String STUDIO_ACTIVITY_COUNT = "studio.activity.count";

  /** Count of non fatal exceptions in the IDE. */
  private static final AtomicLong ourStudioExceptionCount = new AtomicLong(0);
  private static final AtomicLong ourInitialPersistedExceptionCount = new AtomicLong(0);
  private static final AtomicLong ourBundledPluginsExceptionCount = new AtomicLong(0);
  private static final AtomicLong ourNonBundledPluginsExceptionCount = new AtomicLong(0);

  private static final Object EXCEPTION_COUNT_LOCK = new Object();
  @NonNls private static final String STUDIO_EXCEPTION_COUNT_FILE = "studio.exc";
  @NonNls private static final String BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE = "studio.exb";
  @NonNls private static final String NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE = "studio.exp";

  private final ThreadDumpsDatabase myThreadDumpsDatabase = new ThreadDumpsDatabase(new File(PathManager.getTempPath(), "threads.dmp"));

  private static final Object ACTION_INVOCATIONS_LOCK = new Object();
  // Updates to ourActionInvocations need to be done synchronized on ACTION_INVOCATIONS_LOCK to avoid updates during usage reporting.
  private static Map<String, Multiset<InvocationKind>> ourActionInvocations = new HashMap<>();

  private final PropertiesComponent myProperties;

  public SystemHealthMonitor(@NotNull PropertiesComponent properties) {
    myProperties = properties;
  }

  @Override
  public void initComponent() {
    checkRuntime();
    checkReservedCodeCacheSize();
    checkIBus();
    checkSignalBlocking();
    startDiskSpaceMonitoring();

    if (ApplicationManager.getApplication().isInternal() || StatisticsUploadAssistant.isSendAllowed()) {
      ourStudioActionCount.set(myProperties.getOrInitLong(STUDIO_ACTIVITY_COUNT, 0L));
      ourStudioExceptionCount.set(getPersistedExceptionCount(STUDIO_EXCEPTION_COUNT_FILE));
      ourInitialPersistedExceptionCount.set(ourStudioExceptionCount.get());
      ourBundledPluginsExceptionCount.set(getPersistedExceptionCount(BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE));
      ourNonBundledPluginsExceptionCount.set(getPersistedExceptionCount(NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE));

      StudioCrashDetection.updateRecordedVersionNumber(ApplicationInfo.getInstance().getStrictVersion());
      startActivityMonitoring();
      trackCrashes(StudioCrashDetection.reapCrashDescriptions());
      trackPerfWatcherReports(myThreadDumpsDatabase.reapThreadDumps());

      Application application = ApplicationManager.getApplication();
      application.getMessageBus().connect(application).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
        @Override
        public void appClosing() {
          myProperties.setValue(STUDIO_ACTIVITY_COUNT, Long.toString(ourStudioActionCount.get()));
          StudioCrashDetection.stop();
          reportActionInvocations();
        }
      });

      application.getMessageBus().connect(application).subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener.Adapter() {
        @Override
        public void uiFreezeFinished(int lengthInSeconds) {
          // track how long the IDE was frozen
          UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                           .setKind(EventKind.STUDIO_PERFORMANCE_STATS)
                                           .setStudioPerformanceStats(StudioPerformanceStats.newBuilder()
                                                                        .setUiFreezeTimeMs(lengthInSeconds * 1000)));
        }

        @Override
        public void dumpedThreads(@NotNull File toFile, @NotNull ThreadDump dump) {
          // We don't want to add additional overhead when the IDE is already slow, so we just note down the file to which the threads
          // were dumped.
          try {
            myThreadDumpsDatabase.appendThreadDump(toFile.toPath());
          }
          catch (IOException ignored) { // don't worry about errors during analytics events
          }
        }
      });
    }
  }

  private void checkRuntime() {
    try {
      Class.forName("com.sun.jdi.Value");
    } catch (Throwable t) {
      showNotification(new KeyHyperlinkAdapter("unsupported.jre"));
    }

    if (StringUtil.containsIgnoreCase(System.getProperty("java.vm.name", ""), "OpenJDK") &&
        !SystemInfo.isJetbrainsJvm && !SystemInfo.isStudioJvm) {
      showNotification(new KeyHyperlinkAdapter("unsupported.jvm.openjdk.message"));
    }

    if (StringUtil.endsWithIgnoreCase(System.getProperty("java.version", ""), "-ea")) {
      showNotification("unsupported.jvm.ea.message", null);
    }

    JdkBundle bundle = JdkBundle.createBoot();
    if (bundle != null && !bundle.isBundled()) {
      Version version = bundle.getVersion();
      Integer updateNumber = bundle.getUpdateNumber();
      boolean isRuntimeOutdated = version != null && updateNumber != null && version.major == 1 && version.minor == 8 && updateNumber < LATEST_JDK8_UPDATE;
      if (!SystemInfo.isJetBrainsJvm || isRuntimeOutdated) {
        String runtimeVersion = version.toCompactString() + "u" + updateNumber;
        boolean isBundledJdkValid = false;

        final File bundledJDKAbsoluteLocation = JdkBundle.getBundledJDKAbsoluteLocation();
        if (bundledJDKAbsoluteLocation.exists() && bundle.getBitness() == Bitness.x64) {
          if (SystemInfo.isMacIntel64) {
            isBundledJdkValid = true;
          }
          else if (SystemInfo.isWindows || SystemInfo.isLinux) {
            JdkBundle bundledJdk = JdkBundle.createBundle(bundledJDKAbsoluteLocation, false, false);
            if (bundledJdk != null && bundledJdk.getVersion() != null) {
              isBundledJdkValid = true; // Version of bundled jdk is available, so the jdk is compatible with underlying OS
            }
          }
        }

        NotificationAction switchAction = new NotificationAction("Switch") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
            notification.expire();
            ActionManager.getInstance().getAction(SWITCH_JDK_ACTION).actionPerformed(null);
          }
        };

        if (isRuntimeOutdated) {
          showNotification(isBundledJdkValid ? "outdated.jre.version.message1" : "outdated.jre.version.message2",
                           isBundledJdkValid ? switchAction : null, runtimeVersion, LATEST_JDK_RELEASE);
        }
        else if (isBundledJdkValid) {
          showNotification("bundled.jre.version.message", switchAction, runtimeVersion);
        }
      }
    }

    if (SystemInfoRt.isMac &&
        !SystemInfo.isJetbrainsJvm &&
        !SystemInfo.isStudioJvm &&
        SystemInfo.isJavaVersionAtLeast("1.8.0_60") &&
        !SystemInfo.isJavaVersionAtLeast("1.8.0_76")) {
      // Upstream JDK8 bug tracked by https://bugs.openjdk.java.net/browse/JDK-8134917, affecting 1.8.0_60 up to 1.8.0_76.
      // Fixed by Jetbrains in their 1.8.0_40-b108 JRE and tracked in https://youtrack.jetbrains.com/issue/IDEA-146691
      showNotification(new KeyHyperlinkAdapter("unsupported.jvm.dragndrop.message"));
    }
  }

  private void checkReservedCodeCacheSize() {
    int minReservedCodeCacheSize = 240;
    int reservedCodeCacheSize = VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, true);
    if (reservedCodeCacheSize > 0 && reservedCodeCacheSize < minReservedCodeCacheSize) {
      EditCustomVmOptionsAction vmEditAction = new EditCustomVmOptionsAction();
      NotificationAction action = new NotificationAction(IdeBundle.message("vmoptions.edit.action")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          notification.expire();
          ActionUtil.performActionDumbAware(vmEditAction, e);
        }
      };
      showNotification("vmoptions.warn.message", vmEditAction.isEnabled() ? action : null, reservedCodeCacheSize, minReservedCodeCacheSize);
    }
  }

  private void checkIBus() {
    if (SystemInfo.isXWindow) {
      String xim = System.getenv("XMODIFIERS");
      if (xim != null && xim.contains("im=ibus")) {
        String version = ExecUtil.execAndReadLine(new GeneralCommandLine("ibus-daemon", "--version"));
        if (version != null) {
          Matcher m = Pattern.compile("ibus-daemon - Version ([0-9.]+)").matcher(version);
          if (m.find() && StringUtil.compareVersionNumbers(m.group(1), "1.5.11") < 0) {
            String fix = System.getenv("IBUS_ENABLE_SYNC_MODE");
            if (fix == null || fix.isEmpty() || fix.equals("0") || fix.equalsIgnoreCase("false")) {
              showNotification("ibus.blocking.warn.message", new BrowseNotificationAction(IdeBundle.message("ibus.blocking.details.action"),
                                                                                          IdeBundle.message("ibus.blocking.details.url")));
            }
          }
        }
      }
    }
  }

  private static void trackPerfWatcherReports(@NotNull List<Path> threadDumps) {
    if (threadDumps.isEmpty()) {
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      threadDumps.stream()
        .limit(10) // an arbitrary limit, we don't want to overload the backend with too many of these..
        .forEach(t -> {
          List<String> lines;
          try {
            lines = java.nio.file.Files.readAllLines(t);
          }
          catch (IOException e) {
            return;
          }

          reportAnr(t.getFileName().toString(), lines);
        });
    });
  }

  public static void trackCrashes(@NotNull List<String> descriptions) {
    if (descriptions.isEmpty()) {
      return;
    }

    reportCrashes(descriptions);
    trackExceptionsAndActivity(0, 0, 0, 0, descriptions.size(), Collections.emptyList());
  }

  public static void trackExceptionsAndActivity(final long activityCount,
                                                final long exceptionCount,
                                                final long bundledPluginExceptionCount,
                                                final long nonBundledPluginExceptionCount,
                                                final long fatalExceptionCount,
                                                @NotNull List<StackTrace> stackTraces) {
    if (!StatisticsUploadAssistant.isSendAllowed()) {
      return;
    }

    if (!ApplicationManager.getApplication().isInternal()) {
      List<StudioExceptionDetails> allDetails = stackTraces.stream()
        .map(t -> StudioExceptionDetails.newBuilder()
          .setHash(t.md5string())
          .setCount(t.count())
          .setSummary(t.summarize(20))
          .build())
        .collect(Collectors.toList());
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(EventCategory.PING)
                                       .setKind(EventKind.STUDIO_CRASH)
                                       .setStudioCrash(StudioCrash.newBuilder()
                                                         .setActions(activityCount)
                                                         .setExceptions(exceptionCount)
                                                         .setBundledPluginExceptions(bundledPluginExceptionCount)
                                                         .setNonBundledPluginExceptions(nonBundledPluginExceptionCount)
                                                         .setCrashes(fatalExceptionCount)
                                                         .addAllDetails(allDetails)));
    }
  }

  private void checkSignalBlocking() {
    if (SystemInfo.isUnix && JnaLoader.isLoaded()) {
      try {
        LibC lib = Native.loadLibrary("c", LibC.class);
        Memory buf = new Memory(1024);
        if (lib.sigaction(LibC.SIGINT, null, buf) == 0) {
          long handler = Native.POINTER_SIZE == 8 ? buf.getLong(0) : buf.getInt(0);
          if (handler == LibC.SIG_IGN) {
            showNotification("ide.sigint.ignored.message", new BrowseNotificationAction(IdeBundle.message("ide.sigint.ignored.action"),
                                                                                        IdeBundle.message("ide.sigint.ignored.url")));
          }
        }
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    }
  }

  private void showNotification(@PropertyKey(resourceBundle = "messages.IdeBundle") String key,
                                @Nullable NotificationAction action,
                                Object... params) {
    boolean ignored = myProperties.isValueSet("ignore." + key);
    LOG.info("issue detected: " + key + (ignored ? " (ignored)" : ""));
    if (ignored) return;

    String message = IdeBundle.message(key, params);

    Application app = ApplicationManager.getApplication();
    app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appStarting(@Nullable Project projectFromCommandLine) {
        app.invokeLater(() -> {
          Notification notification = new MyNotification(message);
          if (action != null) {
            notification.addAction(action);
          }
          notification.addAction(new NotificationAction(IdeBundle.message("sys.health.acknowledge.action")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
              notification.expire();
              myProperties.setValue("ignore." + key, "true");
            }
          });
          notification.setImportant(true);
          Notifications.Bus.notify(notification);
        });
      }
    });
  }

  private static final class MyNotification extends Notification implements NotificationFullContent {
    public MyNotification(@NotNull String content) {
      super(GROUP.getDisplayId(), "", content, NotificationType.WARNING, new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
          BrowserUtil.browse(e.getDescription());
        }
      });
    }
  }

  private static void startDiskSpaceMonitoring() {
    if (SystemProperties.getBooleanProperty("idea.no.system.path.space.monitoring", false)) {
      return;
    }

    final File file = new File(PathManager.getSystemPath());
    final AtomicBoolean reported = new AtomicBoolean();
    final ThreadLocal<Future<Long>> ourFreeSpaceCalculation = new ThreadLocal<>();

    JobScheduler.getScheduler().schedule(new Runnable() {
      private static final long LOW_DISK_SPACE_THRESHOLD = 50 * 1024 * 1024;
      private static final long MAX_WRITE_SPEED_IN_BPS = 500 * 1024 * 1024;  // 500 MB/sec is near max SSD sequential write speed

      @Override
      public void run() {
        if (!reported.get()) {
          Future<Long> future = ourFreeSpaceCalculation.get();
          if (future == null) {
            ourFreeSpaceCalculation.set(future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
              // file.getUsableSpace() can fail and return 0 e.g. after MacOSX restart or awakening from sleep
              // so several times try to recalculate usable space on receiving 0 to be sure
              long fileUsableSpace = file.getUsableSpace();
              while (fileUsableSpace == 0) {
                TimeoutUtil.sleep(5000);  // hopefully we will not hummer disk too much
                fileUsableSpace = file.getUsableSpace();
              }

              return fileUsableSpace;
            }));
          }
          if (!future.isDone() || future.isCancelled()) {
            restart(1);
            return;
          }

          try {
            final long fileUsableSpace = future.get();
            final long timeout = Math.min(3600, Math.max(5, (fileUsableSpace - LOW_DISK_SPACE_THRESHOLD) / MAX_WRITE_SPEED_IN_BPS));
            ourFreeSpaceCalculation.set(null);

            if (fileUsableSpace < LOW_DISK_SPACE_THRESHOLD) {
              if (ReadAction.compute(() -> NotificationsConfiguration.getNotificationsConfiguration()) == null) {
                ourFreeSpaceCalculation.set(future);
                restart(1);
                return;
              }
              reported.compareAndSet(false, true);

              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(() -> {
                String productName = ApplicationNamesInfo.getInstance().getFullProductName();
                String message = IdeBundle.message("low.disk.space.message", productName);
                if (fileUsableSpace < 100 * 1024) {
                  LOG.warn(message + " (" + fileUsableSpace + ")");
                  Messages.showErrorDialog(message, "Fatal Configuration Problem");
                  reported.compareAndSet(true, false);
                  restart(timeout);
                }
                else {
                  GROUP.createNotification(message, file.getPath(), NotificationType.ERROR, null).whenExpired(() -> {
                    reported.compareAndSet(true, false);
                    restart(timeout);
                  }).notify(null);
                }
              });
            }
            else {
              restart(timeout);
            }
          }
          catch (Exception ex) {
            LOG.error(ex);
          }
        }
      }

      private void restart(long timeout) {
        JobScheduler.getScheduler().schedule(this, timeout, TimeUnit.SECONDS);
      }
    }, 1, TimeUnit.SECONDS);
  }

  @SuppressWarnings({"SpellCheckingInspection", "SameParameterValue"})
  private interface LibC extends Library {
    int SIGINT = 2;
    long SIG_IGN = 1L;
    int sigaction(int signum, Pointer act, Pointer oldact);
  }

  private static final int INITIAL_DELAY_MINUTES = 1; // send out pending activity soon after startup
  private static final int INTERVAL_IN_MINUTES = 30;

  private static void startActivityMonitoring() {
    JobScheduler.getScheduler().scheduleWithFixedDelay(() -> {
      long activityCount = ourStudioActionCount.getAndSet(0);
      long exceptionCount = ourStudioExceptionCount.getAndSet(0);
      long bundledPluginExceptionCount = ourBundledPluginsExceptionCount.getAndSet(0);
      long nonBundledPluginExceptionCount = ourNonBundledPluginsExceptionCount.getAndSet(0);
      persistExceptionCount(0, STUDIO_EXCEPTION_COUNT_FILE);
      persistExceptionCount(0, BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
      persistExceptionCount(0, NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
      if (ApplicationManager.getApplication().isInternal()) {
        // should be 0, but accounting for possible crashes in other threads..
        assert getPersistedExceptionCount(STUDIO_EXCEPTION_COUNT_FILE) < 5;
      }

      if (activityCount > 0 || exceptionCount > 0) {
        List<StackTrace> traces = ExceptionRegistry.INSTANCE.getStackTraces(0);
        ExceptionRegistry.INSTANCE.clear();
        trackExceptionsAndActivity(activityCount, exceptionCount, bundledPluginExceptionCount, nonBundledPluginExceptionCount, 0, traces);
      }
      reportActionInvocations();
    }, INITIAL_DELAY_MINUTES, INTERVAL_IN_MINUTES, TimeUnit.MINUTES);
  }

  public static void incrementAndSaveExceptionCount() {
    persistExceptionCount(ourStudioExceptionCount.incrementAndGet(), STUDIO_EXCEPTION_COUNT_FILE);
    if (ApplicationManager.getApplication().isInternal()) {
      // should be 0, but accounting for possible crashes in other threads..
      assert Math.abs(getPersistedExceptionCount(STUDIO_EXCEPTION_COUNT_FILE) - ourStudioExceptionCount.get()) < 5;
    }
  }

  public static void incrementAndSaveBundledPluginsExceptionCount() {
    persistExceptionCount(ourBundledPluginsExceptionCount.incrementAndGet(), BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
  }

  public static void incrementAndSaveNonBundledPluginsExceptionCount() {
    persistExceptionCount(ourNonBundledPluginsExceptionCount.incrementAndGet(), NON_BUNDLED_PLUGINS_EXCEPTION_COUNT_FILE);
  }

  private static void persistExceptionCount(long count, @NotNull String countFileName) {
    synchronized (EXCEPTION_COUNT_LOCK) {
      try {
        File f = new File(PathManager.getTempPath(), countFileName);
        Files.write(Long.toString(count), f, Charsets.UTF_8);
      }
      catch (Throwable ignored) {
      }
    }
  }

  private static long getPersistedExceptionCount(@NotNull String countFileName) {
    synchronized (EXCEPTION_COUNT_LOCK) {
      try {
        File f = new File(PathManager.getTempPath(), countFileName);
        String contents = Files.toString(f, Charsets.UTF_8);
        return Long.parseLong(contents);
      }
      catch (Throwable t) {
        return 0;
      }
    }
  }

  /**
   * Collect usage stats for action invocations.
   */
  public static void countActionInvocation(@NotNull Class actionClass, @NotNull Presentation templatePresentation, @NotNull AnActionEvent event) {
    synchronized (ACTION_INVOCATIONS_LOCK) {
      String actionName = getActionName(actionClass, templatePresentation);
      InvocationKind invocationKind = getInvocationKindFromEvent(event);

      // We aggregate actions the user takes many times in the course of editing code (key events, copy/paste etc...)
      // other actions are logged directly (our logging mechanism batches the uploads, but timestamps will be accurate).
      if (shouldAggregate(actionClass)) {
        Multiset<InvocationKind> invocations = ourActionInvocations.get(actionName);
        if (invocations == null) {
          invocations = LinkedHashMultiset.create();
          ourActionInvocations.put(actionName, invocations);
        }
        invocations.add(invocationKind);
      } else {
        UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                         .setCategory(EventCategory.STUDIO_UI)
                                         .setKind(EventKind.STUDIO_UI_ACTION_STATS)
                                         .setUiActionStats(UIActionStats.newBuilder()
                                                             .setActionClassName(actionName)
                                                             .setInvocationKind(invocationKind)
                                                             .setInvocations(1)
                                                             .setDirect(true)));
      }
    }
  }

  /**
   * Checks if the action is one we need to aggregate.
   * We only aggregate actions the user takes many times in the course of editing code (key events, copy/paste etc...).
   */
  private static boolean shouldAggregate(Class actionClass) {
    return EditorAction.class.isAssignableFrom(actionClass)
           || UndoRedoAction.class.isAssignableFrom(actionClass)
           || PasteAction.class.isAssignableFrom(actionClass)
           || CopyAction.class.isAssignableFrom(actionClass)
           || CutAction.class.isAssignableFrom(actionClass)
           || SaveAllAction.class.isAssignableFrom(actionClass)
           || DeleteAction.class.isAssignableFrom(actionClass)
           || NextOccurenceAction.class.isAssignableFrom(actionClass)
           || PreviousOccurenceAction.class.isAssignableFrom(actionClass);
  }

  /**
   * Takes the current stats on action invocations and reports them through the {@link UsageTracker}.
   * Resets invocation counts by clearing the map.
   */
  private static void reportActionInvocations() {
    Map<String, Multiset<InvocationKind>> currentInvocations = null;
    synchronized (ACTION_INVOCATIONS_LOCK) {
      currentInvocations = ourActionInvocations;
      ourActionInvocations = new HashMap<>();
    }

    for (Map.Entry<String, Multiset<InvocationKind>> actionEntry : currentInvocations.entrySet()) {
      for (Multiset.Entry<InvocationKind> invocationEntry : actionEntry.getValue().entrySet()) {
        UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(EventCategory.STUDIO_UI)
                                       .setKind(EventKind.STUDIO_UI_ACTION_STATS)
                                       .setUiActionStats(UIActionStats.newBuilder()
                                                         .setActionClassName(actionEntry.getKey())
                                                         .setInvocationKind(invocationEntry.getElement())
                                                         .setInvocations(invocationEntry.getCount())));
      }
    }
  }

  /**
   * Determines the way an event was invoked for usage tracking.
   */
  private static InvocationKind getInvocationKindFromEvent(AnActionEvent event) {
    if (event.getInputEvent() instanceof KeyEvent) {
      return InvocationKind.KEYBOARD_SHORTCUT;
    }
    String place = event.getPlace();
    if (place.contains("Menu")) {
      return InvocationKind.MENU;
    }
    if (place.contains("Toolbar")) {
      return InvocationKind.TOOLBAR;
    }
    if (event.getInputEvent() instanceof MouseEvent) {
      return InvocationKind.MOUSE;
    }
    return InvocationKind.UNKNOWN_INVOCATION_KIND;
  }

  /**
   * Gets an action name based on its class. For Android Studio code, we use simple names for plugins we use canonical names.
   */
  static String getActionName(@NotNull Class actionClass, @NotNull Presentation templatePresentation) {
    Class currentClass = actionClass;
    while (currentClass.isAnonymousClass()) {
      currentClass = currentClass.getEnclosingClass();
    }
    String packageName = currentClass.getPackage().getName();
    if (packageName.startsWith("com.android.") || packageName.startsWith("com.intellij.") || packageName.startsWith("org.jetbrains.") ||
        packageName.startsWith("or.intellij.") || packageName.startsWith("com.jetbrains.") || packageName.startsWith("git4idea.")) {

      String actionName = currentClass.getSimpleName();
      // ExecutorAction points to many different Run/Debug actions, we use the template text to distinguish.
      if (actionName.equals("ExecutorAction")) {
        actionName += "#" + templatePresentation.getText();
      }
      return actionName;
    }
    return currentClass.getCanonicalName();
  }

  public static void reportException(@NotNull Throwable t, @NotNull StackTrace stackTrace) {
    if (!UsageTracker.getInstance().getAnalyticsSettings().hasOptedIn()) {
      return;
    }

    ErrorReportSubmitter reporter = IdeErrorsDialog.getAndroidErrorReporter();
    if (reporter != null) {
      IdeaLoggingEvent e = new AndroidStudioExceptionEvent(t.getMessage(), t, stackTrace);
      reporter.submit(new IdeaLoggingEvent[]{e}, null, null, info -> {
      });
    }
  }

  private static void reportAnr(@NotNull String fileName, @NotNull List<String> threadDump) {
    if (!UsageTracker.getInstance().getAnalyticsSettings().hasOptedIn()) {
      return;
    }

    ErrorReportSubmitter reporter = IdeErrorsDialog.getAndroidErrorReporter();
    if (reporter != null) {
      IdeaLoggingEvent e = new AndroidStudioAnrEvent(fileName, Joiner.on('\n').join(threadDump));
      reporter.submit(new IdeaLoggingEvent[]{e}, null, null, info -> {
      });
    }
  }

  private static void reportCrashes(@NotNull List<String> descriptions) {
    if (!UsageTracker.getInstance().getAnalyticsSettings().hasOptedIn()) {
      return;
    }

    ErrorReportSubmitter reporter = IdeErrorsDialog.getAndroidErrorReporter();
    if (reporter != null) {
      IdeaLoggingEvent e = new AndroidStudioCrashEvents(descriptions);
      reporter.submit(new IdeaLoggingEvent[]{e}, null, null, info -> {
      });
    }
  }

  private static class AndroidStudioExceptionEvent extends IdeaLoggingEvent {
    private final StackTrace myStackTrace;

    public AndroidStudioExceptionEvent(String message, Throwable throwable, @NotNull StackTrace stackTrace) {
      super(message, throwable);
      myStackTrace = stackTrace;
    }

    @Nullable
    @Override
    public Object getData() {
      return ImmutableMap.of("Type", "Exception", // keep consistent with the error reporter in android plugin
                             "md5", myStackTrace.md5string(),
                             "summary", myStackTrace.summarize(50));
    }
  }

  private static class AndroidStudioAnrEvent extends IdeaLoggingEvent {
    private final String myFileName;
    private final String myThreadDump;

    public AndroidStudioAnrEvent(@NotNull String fileName, @NotNull String threadDump) {
      super("", null);
      myFileName = fileName;
      myThreadDump = threadDump;
    }

    @Nullable
    @Override
    public Object getData() {
      return ImmutableMap.of("Type", "ANR", // keep consistent with the error reporter in android plugin
                             "file", myFileName,
                             "threadDump", myThreadDump);
    }
  }

  private static class AndroidStudioCrashEvents extends IdeaLoggingEvent {
    private List<String> myDescriptions;

    public AndroidStudioCrashEvents(@NotNull List<String> descriptions) {
      super("", null);
      myDescriptions = descriptions;
    }

    @Nullable
    @Override
    public Object getData() {
      return ImmutableMap.of("Type", "Crashes", // keep consistent with the error reporter in android plugin
                             "descriptions", myDescriptions);
    }
  }

  class KeyHyperlinkAdapter extends HyperlinkAdapter {
    private final String key;

    KeyHyperlinkAdapter(String key) {
      this.key = key;
    }

    public String getKey() {
      return key;
    }

    public String getIgnoreKey() {
      return "ignore." + key;
    }

    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      String url = e.getDescription();
      if ("ack".equals(url)) {
        myProperties.setValue(getIgnoreKey(), "true");
      }
      else {
        BrowserUtil.browse(url);
      }
    }
  }
}