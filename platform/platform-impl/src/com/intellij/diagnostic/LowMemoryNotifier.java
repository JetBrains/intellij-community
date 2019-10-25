// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.diagnostic.hprof.action.HeapDumpSnapshotRunnable;
import com.intellij.diagnostic.report.MemoryReportReason;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.IdeBundle;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.LowMemoryWatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC;

public class LowMemoryNotifier implements Disposable {
  private static final String PERFORMANCE = "performance";
  private static final int UI_RESPONSE_LOGGING_INTERVAL_MS = 100_000;
  private static final int TOLERABLE_UI_LATENCY = 100;

  private final LowMemoryWatcher myWatcher = LowMemoryWatcher.register(this::onLowMemorySignalReceived, ONLY_AFTER_GC);
  private final AtomicBoolean myNotificationShown = new AtomicBoolean();
  private volatile long myPreviousLoggedUIResponse = 0;

  public LowMemoryNotifier() {
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener() {
      @Override
      public void uiFreezeFinished(long durationMs, @Nullable File reportDir) {
        LifecycleUsageTriggerCollector.onFreeze(durationMs);
      }

      @Override
      public void uiResponded(long latencyMs) {
        final long currentTime = System.currentTimeMillis();
        if (currentTime - myPreviousLoggedUIResponse >= UI_RESPONSE_LOGGING_INTERVAL_MS) {
          myPreviousLoggedUIResponse = currentTime;
          FUCounterUsageLogger.getInstance().logEvent(PERFORMANCE, "ui.latency", new FeatureUsageData().addData("duration_ms", latencyMs));
        }
        if (latencyMs >= TOLERABLE_UI_LATENCY) {
          FUCounterUsageLogger.getInstance().logEvent(PERFORMANCE, "ui.lagging", new FeatureUsageData().addData("duration_ms", latencyMs));
        }
      }
    });
  }

  private void onLowMemorySignalReceived() {
    if (myNotificationShown.compareAndSet(false, true)) {
      Notification notification = new Notification(IdeBundle.message("low.memory.notification.title"),
                                                   IdeBundle.message("low.memory.notification.title"),
                                                   IdeBundle.message("low.memory.notification.content"),
                                                   NotificationType.WARNING);
      notification.addAction(new NotificationAction(IdeBundle.message("low.memory.notification.analyze.action")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          new HeapDumpSnapshotRunnable(MemoryReportReason.UserInvoked, HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START).run();
          notification.expire();
        }
      });

      notification.addAction(new NotificationAction(IdeBundle.message("low.memory.notification.action")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          Runtime rt = Runtime.getRuntime();
          new EditXmxVMOptionDialog(rt.freeMemory(), rt.maxMemory()).show();
          notification.expire();
        }
      });
      Notifications.Bus.notify(notification);
    }
  }

  @Override
  public void dispose() {
    myWatcher.stop();
  }
}
