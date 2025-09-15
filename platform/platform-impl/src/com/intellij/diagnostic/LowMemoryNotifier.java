// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.diagnostic.VMOptions.MemoryKind;
import com.intellij.diagnostic.hprof.action.HeapDumpSnapshotRunnable;
import com.intellij.diagnostic.report.MemoryReportReason;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC;

final class LowMemoryNotifier implements Disposable {
  private static final Set<MemoryKind> ourNotifications = ConcurrentCollectionFactory.createConcurrentSet();

  private final LowMemoryWatcher myWatcher =
    LowMemoryWatcher.register(() -> showNotification(MemoryKind.HEAP, false), ONLY_AFTER_GC);

  @Override
  public void dispose() {
    myWatcher.stop();
  }

  static void showNotificationFromCrashAnalysis() {
    showNotification(MemoryKind.HEAP, true, true);
  }

  static void showNotification(@NotNull MemoryKind kind, boolean oomError) {
    showNotification(kind, oomError, false);
  }

  private static void showNotification(@NotNull MemoryKind kind, boolean oomError, boolean fromCrashReport) {
    int currentXmx = VMOptions.readOption(MemoryKind.HEAP, true);
    var projects = ProjectManager.getInstance().getOpenProjects();
    int projectCount = projects.length;
    boolean isDumb = ContainerUtil.exists(projects, p -> DumbService.isDumb(p));
    UILatencyLogger.lowMemory(kind, currentXmx, projectCount, oomError, fromCrashReport, isDumb);

    if (isDumb
        && kind == MemoryKind.HEAP
        && !oomError
        && !fromCrashReport
        && Registry.is("ide.suppress.low.memory.notifications.when.dumb")) {
      Logger.getInstance(LowMemoryNotifier.class).info("Skipped Low Memory notifications because indexing is in progress");
      return;
    }

    if (!ourNotifications.add(kind)) return;

    var message = oomError ? IdeBundle.message("low.memory.notification.error", kind.label()) : IdeBundle.message("low.memory.notification.warning");
    var type = oomError ? NotificationType.ERROR : NotificationType.WARNING;
    var notification = new Notification("Low Memory", IdeBundle.message("low.memory.notification.title"), message, type);

    if (!fromCrashReport) {
      notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("low.memory.notification.analyze.action"), () ->
        new HeapDumpSnapshotRunnable(MemoryReportReason.UserInvoked,
                                     HeapDumpSnapshotRunnable.AnalysisOption.SCHEDULE_ON_NEXT_START).run()));
    }

    if (VMOptions.canWriteOptions()) {
      notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("low.memory.notification.action"),
                                                                     () -> new EditMemorySettingsDialog(kind).show()));
    }

    notification.whenExpired(() -> ourNotifications.remove(kind));

    Notifications.Bus.notify(notification);
  }
}
