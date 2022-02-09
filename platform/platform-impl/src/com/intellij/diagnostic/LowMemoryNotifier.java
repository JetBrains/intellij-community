// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.diagnostic.hprof.action.HeapDumpSnapshotRunnable;
import com.intellij.diagnostic.report.MemoryReportReason;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.LowMemoryWatcher;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC;

final class LowMemoryNotifier implements Disposable {
  private final LowMemoryWatcher myWatcher = LowMemoryWatcher.register(this::onLowMemorySignalReceived, ONLY_AFTER_GC);
  private final AtomicBoolean myNotificationShown = new AtomicBoolean();

  private void onLowMemorySignalReceived() {
    if (myNotificationShown.compareAndSet(false, true)) {
      showNotification(VMOptions.MemoryKind.HEAP, false);
    }
  }

  @Override
  public void dispose() {
    myWatcher.stop();
  }

  static void showNotification(@NotNull VMOptions.MemoryKind kind, boolean error) {
    String title = IdeBundle.message("low.memory.notification.title");
    String message = IdeBundle.message("low.memory.notification.content");
    NotificationType type = error ? NotificationType.ERROR : NotificationType.WARNING;
    Notification notification = NotificationGroupManager.getInstance().getNotificationGroup("Low Memory").createNotification(title, message, type);

    notification.addAction(new NotificationAction(IdeBundle.message("low.memory.notification.analyze.action")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        new HeapDumpSnapshotRunnable(MemoryReportReason.UserInvoked, HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START).run();
        notification.expire();
      }
    });

    if (VMOptions.canWriteOptions()) {
      notification.addAction(new NotificationAction(IdeBundle.message("low.memory.notification.action")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          new EditMemorySettingsDialog(kind).show();
          notification.expire();
        }
      });
    }

    Notifications.Bus.notify(notification);
  }
}
