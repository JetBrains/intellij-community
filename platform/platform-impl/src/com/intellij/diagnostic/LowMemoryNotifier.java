// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.diagnostic.hprof.action.HeapDumpSnapshotRunnable;
import com.intellij.diagnostic.report.MemoryReportReason;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
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
    var message = error ? IdeBundle.message("low.memory.notification.error", kind.label()) : IdeBundle.message("low.memory.notification.warning");
    var type = error ? NotificationType.ERROR : NotificationType.WARNING;
    var notification = new Notification("Low Memory", IdeBundle.message("low.memory.notification.title"), message, type);

    notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("low.memory.notification.analyze.action"), () ->
      new HeapDumpSnapshotRunnable(MemoryReportReason.UserInvoked, HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START).run()));

    if (VMOptions.canWriteOptions()) {
      notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("low.memory.notification.action"), () -> new EditMemorySettingsDialog(kind).show()));
    }

    Notifications.Bus.notify(notification);
  }
}
