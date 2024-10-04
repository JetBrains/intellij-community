// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Manage and show top-level notifications.
 * <p>
 * Use {@link Bus#notify(Notification)} or {@link Bus#notify(Notification, Project)} (when Project is known) to show notification.
 * <p>
 * See <a href="https://plugins.jetbrains.com/docs/intellij/notifications.html#top-level-notifications">Notifications</a>.
 */
public interface Notifications {

  @Topic.AppLevel
  @Topic.ProjectLevel
  Topic<Notifications> TOPIC = new Topic<>("Notifications", Notifications.class, Topic.BroadcastDirection.NONE);

  /**
   * @deprecated Please use dedicated notification groups for your notifications
   */
  @Deprecated(forRemoval = true)
  String SYSTEM_MESSAGES_GROUP_ID = "System Messages";

  default void notify(@NotNull Notification notification) { }

  default void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType defaultDisplayType) { }

  default void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType defaultDisplayType, boolean shouldLog) { }

  default void register(@NotNull String groupDisplayName,
                        @NotNull NotificationDisplayType defaultDisplayType,
                        boolean shouldLog,
                        boolean shouldReadAloud) { }

  default void register(@NotNull String groupDisplayName,
                        @NotNull NotificationDisplayType defaultDisplayType,
                        boolean shouldLog,
                        boolean shouldReadAloud,
                        @Nullable String toolWindowId) { }

  final class Bus {
    private Bus() { }

    /**
     * Use {@link #notify(Notification, Project)} when project is known to show it in its associated frame.
     */
    public static void notify(@NotNull Notification notification) {
      notify(notification, null);
    }

    public static void notify(@NotNull Notification notification, @Nullable Project project) {
      notification.assertHasTitleOrContent();
      Application app = ApplicationManager.getApplication();
      if (app.isUnitTestMode() || app.isDispatchThread()) {
        doNotify(notification, project);
      }
      else if (project == null) {
        app.invokeLater(() -> doNotify(notification, null));
      }
      else {
        app.invokeLater(() -> doNotify(notification, project), project.getDisposed());
      }
    }

    private static void doNotify(Notification notification, @Nullable Project project) {
      if (project != null && !project.isDisposed() && !project.isDefault()) {
        project.getMessageBus().syncPublisher(TOPIC).notify(notification);
        Disposable notificationDisposable = () -> notification.expire();
        Disposer.register(project, notificationDisposable);
        notification.whenExpired(() -> Disposer.dispose(notificationDisposable));
      }
      else {
        Application app = ApplicationManager.getApplication();
        if (!app.isDisposed()) {
          app.getMessageBus().syncPublisher(TOPIC).notify(notification);
        }
      }
    }

    @ApiStatus.Experimental
    public static void notifyAndHide(@NotNull Notification notification, @Nullable Project project) {
      notify(notification);
      AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> notification.expire(), 5, TimeUnit.SECONDS);
    }
  }
}
