/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author peter
 */
public final class NotificationGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.NotificationGroup");
  private static final Map<String, NotificationGroup> ourRegisteredGroups = ContainerUtil.newConcurrentMap();

  @NotNull private final String myDisplayId;
  @NotNull private final NotificationDisplayType myDisplayType;
  private final boolean myLogByDefault;
  @Nullable private final String myToolWindowId;

  public NotificationGroup(@NotNull String displayId, @NotNull NotificationDisplayType defaultDisplayType, boolean logByDefault) {
    this(displayId, defaultDisplayType, logByDefault, null);
  }

  public NotificationGroup(@NotNull String displayId,
                           @NotNull NotificationDisplayType defaultDisplayType,
                           boolean logByDefault,
                           @Nullable String toolWindowId) {
    myDisplayId = displayId;
    myDisplayType = defaultDisplayType;
    myLogByDefault = logByDefault;
    myToolWindowId = toolWindowId;

    if (ourRegisteredGroups.containsKey(displayId)) {
      LOG.info("Notification group " + displayId + " is already registered", new Throwable());
    }
    ourRegisteredGroups.put(displayId, this);
  }

  public static NotificationGroup balloonGroup(@NotNull String displayId) {
    return new NotificationGroup(displayId, NotificationDisplayType.BALLOON, true);
  }

  public static NotificationGroup logOnlyGroup(@NotNull String displayId) {
    return new NotificationGroup(displayId, NotificationDisplayType.NONE, true);
  }

  public static NotificationGroup toolWindowGroup(@NotNull String displayId, @NotNull String toolWindowId, final boolean logByDefault) {
    return new NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId);
  }

  public String getDisplayId() {
    return myDisplayId;
  }

  public Notification createNotification(@NotNull final String content, @NotNull final MessageType type) {
    return createNotification(content, type.toNotificationType());
  }

  public Notification createNotification(@NotNull final String content, @NotNull final NotificationType type) {
    return createNotification("", content, type, null);
  }

  public Notification createNotification(@NotNull final String title,
                                         @NotNull final String content,
                                         @NotNull final NotificationType type,
                                         @Nullable NotificationListener listener) {
    return new Notification(myDisplayId, title, content, type, listener);
  }

  @NotNull
  public NotificationDisplayType getDisplayType() {
    return myDisplayType;
  }

  public boolean isLogByDefault() {
    return myLogByDefault;
  }

  @Nullable
  public String getToolWindowId() {
    return myToolWindowId;
  }

  @Nullable
  public static NotificationGroup findRegisteredGroup(String displayId) {
    return ourRegisteredGroups.get(displayId);
  }

  @NotNull
  public static Iterable<NotificationGroup> getAllRegisteredGroups() {
    return ourRegisteredGroups.values();
  }

}
