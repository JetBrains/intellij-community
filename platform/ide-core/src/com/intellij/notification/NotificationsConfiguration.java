// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("IdentifierGrammar")
public abstract class NotificationsConfiguration implements Notifications {
  /**
   * If notification group ID starts with this prefix, it wouldn't be shown in Preferences
   */
  public static final String LIGHTWEIGHT_PREFIX = "LIGHTWEIGHT";

  public abstract boolean areNotificationsEnabled();

  public abstract @NotNull NotificationDisplayType getDisplayType(@NotNull String groupId);

  public abstract @NotNull NotificationAnnouncingMode getNotificationAnnouncingMode();

  public abstract void setNotificationAnnouncingMode(@NotNull NotificationAnnouncingMode mode);

  public abstract void setDisplayType(@NotNull String groupId, @NotNull NotificationDisplayType displayType);

  public abstract void changeSettings(@NotNull String groupId, @NotNull NotificationDisplayType displayType, boolean shouldLog, boolean shouldReadAloud);

  public static NotificationsConfiguration getNotificationsConfiguration() {
    return ApplicationManager.getApplication().getService(NotificationsConfiguration.class);
  }
}
