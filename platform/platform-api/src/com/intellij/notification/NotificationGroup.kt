// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * Groups notifications and allows controlling display options in Settings.
 *
 * @author peter
 */
public final class NotificationGroup {
  private static final Logger LOG = Logger.getInstance(NotificationGroup.class);
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

  public NotificationGroup(@NonNls @NotNull String displayId,
                           @NotNull NotificationDisplayType defaultDisplayType,
                           boolean logByDefault,
                           @NonNls @Nullable String toolWindowId) {
    this(displayId, defaultDisplayType, logByDefault, toolWindowId, null);
  }

  public NotificationGroup(@NonNls @NotNull String displayId,
                           @NotNull NotificationDisplayType defaultDisplayType,
                           boolean logByDefault,
                           @NonNls @Nullable String toolWindowId,
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
  public static NotificationGroup balloonGroup(@NonNls @NotNull String displayId) {
    return new NotificationGroup(displayId, NotificationDisplayType.BALLOON, true);
  }

  @NotNull
  public static NotificationGroup logOnlyGroup(@NonNls @NotNull String displayId) {
    return new NotificationGroup(displayId, NotificationDisplayType.NONE, true);
  }

  @NotNull
  public static NotificationGroup toolWindowGroup(@NonNls @NotNull String displayId, @NonNls @NotNull String toolWindowId, final boolean logByDefault) {
    return new NotificationGroup(displayId, NotificationDisplayType.TOOL_WINDOW, logByDefault, toolWindowId);
  }

  @NotNull
  public static NotificationGroup toolWindowGroup(@NonNls @NotNull String displayId, @NonNls @NotNull String toolWindowId) {
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

  public Notification createNotification(@NotNull @NonNls final String content, @NotNull final MessageType type) {
    return createNotification(content, type.toNotificationType());
  }

  @NotNull
  public Notification createNotification(@NotNull @NonNls final String content, @NotNull final NotificationType type) {
    return createNotification("", content, type, null);
  }

  @NotNull
  public Notification createNotification(@NotNull @NonNls final String title,
                                         @NotNull @NonNls final String content,
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
  public Notification createNotification(@Nullable @NonNls String title,
                                         @Nullable @NonNls String subtitle,
                                         @Nullable @NonNls String content,
                                         @NotNull NotificationType type) {
    return createNotification(title, subtitle, content, type, null);
  }

  @NotNull
  public Notification createNotification(@Nullable @NonNls String title,
                                         @Nullable @NonNls String subtitle,
                                         @Nullable @NonNls String content,
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
