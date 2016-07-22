package org.jetbrains.notification;

import com.intellij.notification.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public final class SingletonNotificationManager {
  private final AtomicReference<Notification> notification = new AtomicReference<>();

  private final NotificationGroup group;
  private final NotificationType type;
  @Nullable
  private final NotificationListener listener;

  private Runnable expiredListener;

  public SingletonNotificationManager(@NotNull NotificationGroup group, @NotNull NotificationType type, @Nullable NotificationListener listener) {
    this.group = group;
    this.type = type;
    this.listener = listener;
  }

  public boolean notify(@NotNull String title, @NotNull String content) {
    return notify(title, content, null);
  }

  public boolean notify(@NotNull String title, @NotNull String content, @Nullable Project project) {
    return notify(title, content, listener, project);
  }

  public boolean notify(@NotNull String content, @Nullable Project project) {
    return notify("", content, listener, project);
  }

  public boolean notify(@NotNull String title,
                        @NotNull String content,
                        @Nullable NotificationListener listener,
                        @Nullable Project project) {
    Notification oldNotification = notification.get();
    // !oldNotification.isExpired() is not enough - notification could be closed, but not expired
    if (oldNotification != null) {
      if (!oldNotification.isExpired() && (oldNotification.getBalloon() != null ||
                                           (project != null &&
                                            group.getDisplayType() == NotificationDisplayType.TOOL_WINDOW &&
                                            ToolWindowManager.getInstance(project).getToolWindowBalloon(group.getToolWindowId()) != null))) {
        return false;
      }
      oldNotification.whenExpired(null);
      oldNotification.expire();
    }

    if (expiredListener == null) {
      expiredListener = () -> {
        Notification currentNotification = notification.get();
        if (currentNotification != null && currentNotification.isExpired()) {
          notification.compareAndSet(currentNotification, null);
        }
      };
    }

    Notification newNotification = group.createNotification(title, content, type, listener);
    newNotification.whenExpired(expiredListener);
    notification.set(newNotification);
    newNotification.notify(project);
    return true;
  }

  public void clear() {
    Notification oldNotification = notification.getAndSet(null);
    if (oldNotification != null) {
      oldNotification.whenExpired(null);
      oldNotification.expire();
    }
  }
}