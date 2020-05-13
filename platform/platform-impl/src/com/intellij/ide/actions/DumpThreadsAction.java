// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class DumpThreadsAction extends AnAction implements DumbAware {
  private final NotificationGroup GROUP = new NotificationGroup("Dump Threads Group", NotificationDisplayType.BALLOON, false, null, null,
                                                                null, PluginManagerCore.CORE_ID);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      File dumpDir = PerformanceWatcher.getInstance().dumpThreads("", false);
      Notification notification = createNotification(dumpDir);
      notification.notify(e.getProject());
    });
  }

  @NotNull
  private Notification createNotification(@Nullable File file) {
    if (file != null) {
      String url = FileUtil.getUrl(file);
      String message = IdeBundle.message("thread.dump.is.taken", url);
      return GROUP.createNotification("", message, NotificationType.INFORMATION, RevealFileAction.FILE_SELECTING_LISTENER);
    }
    else {
      return GROUP.createNotification(IdeBundle.message("failed.to.take.thread.dump"), NotificationType.INFORMATION);
    }
  }
}