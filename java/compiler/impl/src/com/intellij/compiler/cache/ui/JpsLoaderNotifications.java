package com.intellij.compiler.cache.ui;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;

public final class JpsLoaderNotifications {
  public static final NotificationGroup ATTENTION = new NotificationGroup("Compile Output Loader: Attention",
                                                                                    NotificationDisplayType.STICKY_BALLOON, true);
  public static final NotificationGroup STANDARD = new NotificationGroup("Compile Output Loader: Standard",
                                                                                    NotificationDisplayType.BALLOON, true);
  public static final NotificationGroup EVENT_LOG = new NotificationGroup("Compile Output Loader: Event Log",
                                                                                    NotificationDisplayType.NONE, true);
}
