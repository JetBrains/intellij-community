package com.intellij.notification;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author spleaner
 */
public interface Notifications {
  Topic<Notifications> TOPIC = Topic.create("Notifications", Notifications.class);

  void register(@NonNls @NotNull String id, @NotNull NotificationDisplayType defaultDisplayType,
                boolean canDisable);

  void notify(@NonNls @NotNull final String id, @NotNull final String name,
              @NotNull final String description, @NotNull final NotificationType type,
              @NotNull NotificationListener handler);

  void notify(@NonNls @NotNull final String id, @NotNull final String name,
              @NotNull final String description, @NotNull final NotificationType type,
              @NotNull NotificationListener handler, @Nullable Icon icon);

  void invalidateAll(@NotNull String id);
}
