// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public class FormattingUiNotificationService implements FormattingNotificationService {

  private @NotNull final Project myProject;

  public FormattingUiNotificationService(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void reportError(@NotNull String groupId,
                          @NotNull @NlsContexts.NotificationTitle String title,
                          @NotNull @NlsContexts.NotificationContent String message) {
    Notifications.Bus.notify(new Notification(groupId, title, message, NotificationType.ERROR), myProject);
  }
}
