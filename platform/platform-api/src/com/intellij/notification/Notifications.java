/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.notification;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 */
public interface Notifications {
  Topic<Notifications> TOPIC = Topic.create("Notifications", Notifications.class, Topic.BroadcastDirection.NONE);

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

    public static void notify(@NotNull final Notification notification, @NotNull final NotificationDisplayType defaultDisplayType, @Nullable final Project project) {
      if (project != null && !project.isInitialized()) {
        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
           public void run() {
             project.getMessageBus().syncPublisher(TOPIC).notify(notification, defaultDisplayType);
           }
         });
        return;
      }

      final MessageBus bus = project == null ? ApplicationManager.getApplication().getMessageBus() : project.getMessageBus();
      if (EventQueue.isDispatchThread()) bus.syncPublisher(TOPIC).notify(notification, defaultDisplayType);
      else {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            bus.syncPublisher(TOPIC).notify(notification, defaultDisplayType);
          }
        });
      }
    }
  }
}
