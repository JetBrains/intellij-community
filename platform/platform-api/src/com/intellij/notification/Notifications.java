package com.intellij.notification;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public interface Notifications {
  Topic<Notifications> TOPIC = Topic.create("Notifications", Notifications.class);

  String SYSTEM_MESSAGES_GROUP_ID = "System Messages";

  void notify(@NotNull Notification notification);
  void notify(@NotNull Notification notification, @NotNull NotificationDisplayType defaultDisplayType);

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  class Bus {
    public static void notify(@NotNull final Notification notification) {
      notify(notification, NotificationDisplayType.BALLOON, null);
    }

    public static void notify(@NotNull final Notification notification, @Nullable Project project) {
      notify(notification, NotificationDisplayType.BALLOON, project);
    }

    public static void notify(@NotNull final Notification notification, @NotNull final NotificationDisplayType defaultDisplayType, @Nullable Project project) {
      final MessageBus bus = project != null ? project.getMessageBus() : ApplicationManager.getApplication().getMessageBus();
      bus.syncPublisher(TOPIC).notify(notification, defaultDisplayType);

    }
  }
}
