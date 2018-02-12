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

import javax.swing.*;
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
  private final Icon myIcon;

  private String myParentId;

  public NotificationGroup(@NotNull String displayId, @NotNull NotificationDisplayType defaultDisplayType, boolean logByDefault) {
    this(displayId, defaultDisplayType, logByDefault, null);
  }

  public NotificationGroup(@NotNull String displayId,
                           @NotNull NotificationDisplayType defaultDisplayType,
                           boolean logByDefault,
                           @Nullable String toolWindowId) {
    this(displayId, defaultDisplayType, logByDefault, toolWindowId, null);
  }

  public NotificationGroup(@NotNull String displayId,
                           @NotNull NotificationDisplayType defaultDisplayType,
                           boolean logByDefault,
                           @Nullable String toolWindowId,
                           @Nullable Icon icon) {
    myDisplayId = displayId;
    myDisplayType = defaultDisplayType;
    myLogByDefault = logByDefault;
    myToolWindowId = toolWindowId;
    myIcon = icon;

    if (ourRegisteredGroups.containsKey(displayId)) {
      LOG.info("Notification group " + displayId + " is already registered", new Throwable());
    }
    ourRegisteredGroups.put(displayId, this);
  }

  @NotNull
  public static NotificationGroup balloonGroup(@NotNull String displayId) {
    return new NotificationGroup(displayId, NotificationDisplayType.BALLOON, true);
  }

  @NotNull
  public static NotificationGroup logOnlyGroup(@NotNull String displayId) {
    return new NotificationGroup(displayId, NotificationDisplayType.NONE, true);
  }

  @NotNull
  public static NotificationGroup toolWindowGroup(@NotNull String displayId, @NotNull String toolWindowId, final boolean logByDefault) {
    return new NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId);
  }

  @NotNull
  public static NotificationGroup toolWindowGroup(@NotNull String displayId, @NotNull String toolWindowId) {
    return toolWindowGroup(displayId, toolWindowId, true);
  }

  @NotNull
  public String getDisplayId() {
    return myDisplayId;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  public Notification createNotification(@NotNull final String content, @NotNull final MessageType type) {
    return createNotification(content, type.toNotificationType());
  }

  @NotNull
  public Notification createNotification(@NotNull final String content, @NotNull final NotificationType type) {
    return createNotification("", content, type, null);
  }

  @NotNull
  public Notification createNotification(@NotNull final String title,
                                         @NotNull final String content,
                                         @NotNull final NotificationType type,
                                         @Nullable NotificationListener listener) {
    return new Notification(myDisplayId, title, content, type, listener);
  }

  @NotNull
  public Notification createNotification() {
    return createNotification(NotificationType.INFORMATION);
  }

  @NotNull
  public Notification createNotification(@NotNull NotificationType type) {
    return createNotification(null, null, null, type, null);
  }

  @NotNull
  public Notification createNotification(@Nullable String title,
                                         @Nullable String subtitle,
                                         @Nullable String content,
                                         @NotNull NotificationType type) {
    return createNotification(title, subtitle, content, type, null);
  }

  @NotNull
  public Notification createNotification(@Nullable String title,
                                         @Nullable String subtitle,
                                         @Nullable String content,
                                         @NotNull NotificationType type,
                                         @Nullable NotificationListener listener) {
//    LOG.assertTrue(myIcon != null);
    return new Notification(myDisplayId, myIcon, title, subtitle, content, type, listener);
  }

  @Nullable
  public String getParentId() {
    return myParentId;
  }

  @NotNull
  public NotificationGroup setParentId(@NotNull String parentId) {
    myParentId = parentId;
    return this;
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
