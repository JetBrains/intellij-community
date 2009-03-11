package com.intellij.notification.impl;

import org.jetbrains.annotations.NotNull;
import com.intellij.notification.impl.NotificationImpl;

/**
 * @author spleaner
 */
public interface NotificationModelListener {

  void notificationsAdded(@NotNull final NotificationImpl... notification);

  void notificationsRemoved(@NotNull final NotificationImpl... notification);
}
