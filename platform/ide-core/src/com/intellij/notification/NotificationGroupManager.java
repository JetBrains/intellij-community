// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public interface NotificationGroupManager {
  NotificationGroup getNotificationGroup(@NotNull String groupId);

  boolean isGroupRegistered(@NotNull String groupId);

  @NotNull @Unmodifiable
  Collection<NotificationGroup> getRegisteredNotificationGroups();

  boolean isRegisteredNotificationId(@NotNull String notificationId);

  static NotificationGroupManager getInstance() {
    return ApplicationManager.getApplication().getService(NotificationGroupManager.class);
  }
}
