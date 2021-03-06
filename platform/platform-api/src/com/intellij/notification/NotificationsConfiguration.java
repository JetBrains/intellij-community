// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.openapi.application.ApplicationManager;

public abstract class NotificationsConfiguration implements Notifications {
  /**
   * If notification group ID starts with this prefix it wouldn't be shown in Preferences
   */
  public static final String LIGHTWEIGHT_PREFIX = "LIGHTWEIGHT";

  public abstract void changeSettings(String groupDisplayName, NotificationDisplayType displayType, boolean shouldLog, boolean shouldReadAloud);

  public static NotificationsConfiguration getNotificationsConfiguration() {
    return ApplicationManager.getApplication().getService(NotificationsConfiguration.class);
  }
}
