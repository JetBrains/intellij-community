package com.intellij.notification;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public interface Notifications {
  Topic<Notifications> TOPIC = Topic.create("Notifications", Notifications.class);

  public static final String SYSTEM_MESSAGES_ID = "System Messages";

  void register(@NonNls @NotNull String id, @NotNull NotificationDisplayType defaultDisplayType,
                boolean canDisable);

  void notify(@NonNls @NotNull final String id, @NotNull final String name,
              @NotNull final String description, @NotNull final NotificationType type,
              @NotNull NotificationListener handler);

  void invalidateAll(@NotNull String id);
}
