// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.featureStatistics.fusCollectors.AppLifecycleUsageTriggerCollector;
import com.intellij.ide.IdeBundle;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.LowMemoryWatcher;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.featureStatistics.fusCollectors.AppLifecycleUsageTriggerCollector.LIFECYCLE_APP;
import static com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC;

public class LowMemoryNotifier implements Disposable {
  private static final FeatureUsageGroup PERFORMANCE = new FeatureUsageGroup("performance", 1);
  private static final int UI_RESPONSE_LOGGING_INTERVAL_MS = 100_000;
  private static final int TOLERABLE_UI_LATENCY = 100;

  private final LowMemoryWatcher myWatcher = LowMemoryWatcher.register(this::onLowMemorySignalReceived, ONLY_AFTER_GC);
  private final AtomicBoolean myNotificationShown = new AtomicBoolean();
  private volatile long myPreviousLoggedUIResponse = 0;

  public LowMemoryNotifier() {
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener() {
      @Override
      public void uiFreezeFinished(int lengthInSeconds) {
        String lengthGrouped = groupLength(lengthInSeconds);
        FeatureUsageLogger.INSTANCE.log(LIFECYCLE_APP, "ide.freeze", StatisticsUtilKt.createData(null, FUSUsageContext
          .create("timeSecondsGrouped", lengthGrouped)));

        FeatureUsageLogger.INSTANCE.log(AppLifecycleUsageTriggerCollector.LIFECYCLE,
                                        "ide.freeze", Collections.singletonMap("durationSeconds", lengthInSeconds));
      }

      @Override
      public void uiResponded(long latencyMs) {
        final long currentTime = System.currentTimeMillis();
        if (currentTime - myPreviousLoggedUIResponse >= UI_RESPONSE_LOGGING_INTERVAL_MS) {
          myPreviousLoggedUIResponse = currentTime;
          FeatureUsageLogger.INSTANCE.log(PERFORMANCE, "ui.latency", Collections.singletonMap("duration_ms", latencyMs));
        }
        if (latencyMs >= TOLERABLE_UI_LATENCY) {
          FeatureUsageLogger.INSTANCE.log(PERFORMANCE, "ui.lagging", Collections.singletonMap("duration_ms", latencyMs));
        }
      }

      private String groupLength(int seconds) {
        if (seconds >= 60) {
          return "60+";
        }
        if (seconds > 10) {
          seconds -= (seconds % 10);
          return seconds + "+";
        }
        return String.valueOf(seconds);
      }
    });
  }

  private void onLowMemorySignalReceived() {
    if (myNotificationShown.compareAndSet(false, true)) {
      Notification notification = new Notification(IdeBundle.message("low.memory.notification.title"),
                                                   IdeBundle.message("low.memory.notification.title"),
                                                   IdeBundle.message("low.memory.notification.content"),
                                                   NotificationType.WARNING);
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
