package com.intellij.ui;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class Notifications {
  private Notifications() {
  }

  public static void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) {
    if (!SystemInfo.isMac) return;

    GrowlNotifications.getNofications().notify(notificationName, title, text);
  }
}
