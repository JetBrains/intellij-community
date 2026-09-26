// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.notification.NotificationType;
import com.intellij.util.ui.MessageCategory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public enum NotificationCategory {
  ERROR(MessageCategory.ERROR),
  INFO(MessageCategory.INFORMATION),
  SIMPLE(MessageCategory.SIMPLE),
  WARNING(MessageCategory.WARNING);

  private final int myValue;

  NotificationCategory(int value) {
    myValue = value;
  }

  public int getMessageCategory() {
    return myValue;
  }

  public @NotNull NotificationType getNotificationType() {
    return convert(this);
  }

  public static NotificationType convert(NotificationCategory notificationCategory) {
    return switch (notificationCategory) {
      case ERROR -> NotificationType.ERROR;
      case INFO, SIMPLE -> NotificationType.INFORMATION;
      case WARNING -> NotificationType.WARNING;
    };
  }

  public static NotificationCategory convert(NotificationType notificationType) {
    return switch (notificationType) {
      case INFORMATION -> INFO;
      case WARNING -> WARNING;
      case ERROR -> ERROR;
      default -> SIMPLE;
    };
  }

  public static NotificationCategory convert(int type) {
    return switch (type) {
      case MessageCategory.ERROR -> ERROR;
      case MessageCategory.WARNING -> WARNING;
      case MessageCategory.INFORMATION, MessageCategory.STATISTICS, MessageCategory.NOTE -> INFO;
      case MessageCategory.SIMPLE -> SIMPLE;
      default -> SIMPLE;
    };
  }
}
