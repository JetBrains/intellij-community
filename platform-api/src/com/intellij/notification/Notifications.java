package com.intellij.notification;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public interface Notifications {
  Topic<Notifications> TOPIC = Topic.create("Notifications", Notifications.class);

  void register(@NotNull String id, @NotNull NotificationDisplayType defaultDisplayType,
                boolean canDisable);

  void notify(@NotNull final String id, @NotNull final String name,
              @NotNull final String description, @NotNull final NotificationType type,
              @NotNull NotificationListener handler);

  void notify(@NotNull final String id, @NotNull final String name,
              @NotNull final String description, @NotNull final NotificationType type,
              @NotNull NotificationListener handler, @Nullable Icon icon);

  void invalidateAll(@NotNull String id);
}
