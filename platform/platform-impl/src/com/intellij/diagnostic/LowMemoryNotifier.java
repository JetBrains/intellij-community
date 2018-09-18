// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.featureStatistics.fusCollectors.AppLifecycleUsageTriggerCollector;
import com.intellij.ide.IdeBundle;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUSApplicationUsageTrigger;
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

import static com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC;

public class LowMemoryNotifier implements Disposable {
  private final LowMemoryWatcher myWatcher = LowMemoryWatcher.register(this::onLowMemorySignalReceived, ONLY_AFTER_GC);
  private final AtomicBoolean myNotificationShown = new AtomicBoolean();

  public LowMemoryNotifier() {
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener() {
      @Override
      public void uiFreezeFinished(int lengthInSeconds) {
        FUSApplicationUsageTrigger.getInstance().trigger(AppLifecycleUsageTriggerCollector.class, "ide.freeze." + lengthInSeconds);
        FeatureUsageLogger.INSTANCE.log("lifecycle",
                                        "ide.freeze", Collections.singletonMap("durationSeconds", lengthInSeconds));
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
