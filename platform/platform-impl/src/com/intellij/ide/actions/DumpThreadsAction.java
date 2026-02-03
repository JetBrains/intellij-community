// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeDependentAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@ApiStatus.Internal
public final class DumpThreadsAction extends IdeDependentAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    dumpThreads(e.getProject());
  }

  public static void dumpThreads(@Nullable Project project) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Path dumpDir = PerformanceWatcher.getInstance().dumpThreads("", false, false);
      Notification notification = createNotification(dumpDir);
      notification.notify(project);
    });
  }

  private static @NotNull Notification createNotification(@Nullable Path file) {
    NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup("Dump Threads Group");
    if (file != null) {
      String url = FileUtil.getUrl(file.toFile());
      return group.createNotification(IdeBundle.message("thread.dump.is.taken", url), NotificationType.INFORMATION)
        .setListener(RevealFileAction.FILE_SELECTING_LISTENER);
    }
    else {
      return group.createNotification(IdeBundle.message("failed.to.take.thread.dump"), NotificationType.INFORMATION);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
