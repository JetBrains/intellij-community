// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NotificationsManager {
  public static NotificationsManager getNotificationsManager() {
    return ApplicationManager.getApplication().getService(NotificationsManager.class);
  }

  public abstract void showNotification(@NotNull Notification notification, @Nullable Project project);

  public abstract void expire(@NotNull Notification notification);

  public abstract <T extends Notification> T @NotNull [] getNotificationsOfType(@NotNull Class<T> klass, @Nullable Project project);
}
