// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author spleaner
 */
public enum NotificationType {
  IDE_UPDATE,
  INFORMATION,
  WARNING,
  ERROR;

  public static @Nullable NotificationType getDominatingType(@NotNull Collection<? extends Notification> notifications) {
    NotificationType result = null;
    for (Notification notification : notifications) {
      NotificationType type = notification.getType();
      if (ERROR == type) return ERROR;
      else if (WARNING == type) result = WARNING;
      else if (result == null) result = INFORMATION;
    }
    return result;
  }
}
