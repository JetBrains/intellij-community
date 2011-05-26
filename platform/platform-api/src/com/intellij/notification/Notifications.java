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

import com.intellij.ide.FrameStateManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.Processor;
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
  String LOG_ONLY_GROUP_ID = "Log Only";

  void notify(@NotNull Notification notification);
  void register(@NotNull final String group_id, @NotNull final NotificationDisplayType defaultDisplayType);

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  class Bus {

    static {
      register(LOG_ONLY_GROUP_ID, NotificationDisplayType.NONE);
    }

    /**
     * Registration is OPTIONAL: STICKY_BALLOON display type will be used by default.
     */
    public static void register(@NotNull final String group_id, @NotNull final NotificationDisplayType defaultDisplayType) {
      invoke(null, new Processor<MessageBus>() {
        @Override
        public boolean process(final MessageBus bus) {
          bus.syncPublisher(TOPIC).register(group_id, defaultDisplayType);
          return false;
        }
      });
    }

    @Deprecated
    public static void notify(@NotNull final Notification notification, final NotificationDisplayType displayType, @Nullable final Project project) {
      notify(notification, project);
    }

    public static void notify(@NotNull final Notification notification) {
      notify(notification, null);
    }

    public static void notify(@NotNull final Notification notification, @Nullable final Project project) {
      invoke(project, new Processor<MessageBus>() {
        @Override
        public boolean process(final MessageBus messageBus) {
          messageBus.syncPublisher(TOPIC).notify(notification);
          return false;
        }
      });
    }

    private static void invoke(final Project project, final Processor<MessageBus> fun) {
      if (project != null && !project.isDisposed()) {
        if (!project.isInitialized()) {
          StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
            public void run() {
              fun.process(project.getMessageBus());
            }
          });
        }
        else {
          final MessageBus bus = project.getMessageBus();
          notifyLaterIfNeeded(bus, fun);
        }

        return;
      }

      FrameStateManager frameStateManager = FrameStateManager.getInstance();
      if (frameStateManager != null) {
        frameStateManager.getApplicationActive().doWhenDone(new Runnable() {
          @Override
          public void run() {
            Application app = ApplicationManager.getApplication();
            final MessageBus bus =
              project == null ? app.isDisposed() ? null : app.getMessageBus() : project.isDisposed() ? null : project.getMessageBus();
            if (bus != null) {
              notifyLaterIfNeeded(bus, fun);
            }
          }
        });
      }
    }

    private static void notifyLaterIfNeeded(final MessageBus bus,
                                            final Processor<MessageBus> fun) {
      if (EventQueue.isDispatchThread()) {
        fun.process(bus);
      }
      else {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            fun.process(bus);
          }
        });
      }
    }
  }
}
