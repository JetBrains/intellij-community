// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.diagnostic.hprof.action.HeapDumpSnapshotRunnable;
import com.intellij.diagnostic.report.MemoryReportReason;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.LowMemoryWatcher;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC;

final class LowMemoryNotifier implements Disposable {
  private static final Set<VMOptions.MemoryKind> ourNotifications = ConcurrentCollectionFactory.createConcurrentSet();

  private final LowMemoryWatcher myWatcher = LowMemoryWatcher.register(() -> showNotification(VMOptions.MemoryKind.HEAP, false), ONLY_AFTER_GC);

  @Override
  public void dispose() {
    myWatcher.stop();
  }

  static void showNotification(@NotNull VMOptions.MemoryKind kind, boolean error) {
    if (!ourNotifications.add(kind)) return;

    int currentXmx = VMOptions.readOption(VMOptions.MemoryKind.HEAP, true);
    UILatencyLogger.lowMemory(kind, currentXmx);

    var message = error ? IdeBundle.message("low.memory.notification.error", kind.label()) : IdeBundle.message("low.memory.notification.warning");
    var type = error ? NotificationType.ERROR : NotificationType.WARNING;
    var notification = new Notification("Low Memory", IdeBundle.message("low.memory.notification.title"), message, type);

    notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("low.memory.notification.analyze.action"), () ->
      new HeapDumpSnapshotRunnable(MemoryReportReason.UserInvoked, HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START).run()));

    if (VMOptions.canWriteOptions()) {
      notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("low.memory.notification.action"),
                                                                     () -> new EditMemorySettingsDialog(kind).show()));
    }

    notification.whenExpired(() -> ourNotifications.remove(kind));

    Notifications.Bus.notify(notification);
  }
}
