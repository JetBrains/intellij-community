// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Manage and show top-level notifications.
 * <p/>
 * Use {@link Bus#notify(Notification)} or {@link Bus#notify(Notification, Project)} (when Project is known) to show notification.
 * <p>
 * See <a href="www.jetbrains.org/intellij/sdk/docs/user_interface_components/notifications.html#top-level-notifications">Notifications</a>.
 */
public interface Notifications {
  Topic<Notifications> TOPIC = Topic.create("Notifications", Notifications.class, Topic.BroadcastDirection.NONE);

  String SYSTEM_MESSAGES_GROUP_ID = "System Messages";

  default void notify(@NotNull Notification notification) {
  }

  default void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType defaultDisplayType) {
  }

  default void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType defaultDisplayType, boolean shouldLog) {
  }

  default void register(@NotNull String groupDisplayName,
                        @NotNull NotificationDisplayType defaultDisplayType,
                        boolean shouldLog,
                        boolean shouldReadAloud) {
  }

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  final
  class Bus {
    /**
     * Registration is OPTIONAL: BALLOON display type will be used by default.
     *
     * @deprecated use {@link NotificationGroup}
     */
    @Deprecated
    public static void register(@NotNull final String group_id, @NotNull final NotificationDisplayType defaultDisplayType) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        Application app = ApplicationManager.getApplication();
        if (!app.isDisposed()) {
          app.getMessageBus().syncPublisher(TOPIC).register(group_id, defaultDisplayType);
        }
      });
    }

    /**
     * Use {@link #notify(Notification, Project)} when project is known to show it in its associated frame.
     */
    public static void notify(@NotNull final Notification notification) {
      notify(notification, null);
    }

    public static void notify(@NotNull final Notification notification, @Nullable final Project project) {
      notification.assertHasTitleOrContent();
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        doNotify(notification, project);
      }
      else {
        UIUtil.invokeLaterIfNeeded(() -> doNotify(notification, project));
      }
    }

    private static void doNotify(Notification notification, @Nullable Project project) {
      if (project != null && !project.isDisposed() && !project.isDefault()) {
        project.getMessageBus().syncPublisher(TOPIC).notify(notification);
      }
      else {
        Application app = ApplicationManager.getApplication();
        if (!app.isDisposed()) {
          app.getMessageBus().syncPublisher(TOPIC).notify(notification);
        }
      }
    }

    @ApiStatus.Experimental
    public static void notifyAndHide(@NotNull final Notification notification) {
      notifyAndHide(notification, null);
    }

    @ApiStatus.Experimental
    public static void notifyAndHide(@NotNull final Notification notification, @Nullable Project project) {
      notify(notification);
      Alarm alarm = new Alarm(project == null ? ApplicationManager.getApplication() : project);
      alarm.addRequest(() -> {
        notification.expire();
        Disposer.dispose(alarm);
      }, 5000);
    }
  }
}
