package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public interface SystemNotifications {
  void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text);
}
