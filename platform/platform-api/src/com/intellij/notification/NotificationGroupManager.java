// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface NotificationGroupManager {
  NotificationGroup getNotificationGroup(@NotNull String groupId);

  @NotNull Collection<NotificationGroup> getRegisteredNotificationGroups();

  boolean isRegisteredNotificationId(@NotNull String notificationId);

  static NotificationGroupManager getInstance() {
    return ApplicationManager.getApplication().getService(NotificationGroupManager.class);
  }
}
