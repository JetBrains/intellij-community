// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CodeStyleSettingsMigrationNotifier implements CodeStyleSettingsMigrationListener {

  private static final NotificationGroup NOTIFICATION_GROUP =
    NotificationGroupManager.getInstance().getNotificationGroup("Code style settings migration");

  @Override
  public void codeStyleSettingsMigrated(@NotNull Project project) {
    doNotify(project);
  }

  private static void doNotify(@NotNull Project project) {
    ApplicationManager.getApplication().invokeLater(() -> {
      Notification notification = new CodeStyleMigrationNotification(project.getName());
      notification.notify(project);
    }, project.getDisposed());
  }

  private static class CodeStyleMigrationNotification extends Notification {
    CodeStyleMigrationNotification(@NotNull String projectName) {
      super(NOTIFICATION_GROUP.getDisplayId(),
            ApplicationBundle.message("project.code.style.migration.title"),
            ApplicationBundle.message("project.code.style.migration.message", projectName),
            NotificationType.INFORMATION);
      addAction(new ShowMoreInfoAction());
    }
  }

  private static class ShowMoreInfoAction extends DumbAwareAction {
    ShowMoreInfoAction() {
      super(IdeBundle.messagePointer("action.ProjectCodeStyleSettingsManager.ShowMoreInfoAction.text.more.info"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      BrowserUtil.open("https://confluence.jetbrains.com/display/IDEADEV/New+project+code+style+settings+format+in+2017.3");
    }
  }

}
