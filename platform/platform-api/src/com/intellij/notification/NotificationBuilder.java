// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/** @deprecated the class is no longer needed, since {@link Notification} itself does implement the builder pattern. */
@Deprecated(forRemoval = true)
public final class NotificationBuilder {
  private final String myGroupId;
  private @NotNull @NlsContexts.NotificationTitle String myTitle;
  private @NotNull @NlsContexts.NotificationContent String myContent;
  private @NotNull NotificationType myType;
  private @Nullable NotificationListener myListener;
  private @Nullable @NonNls String myNotificationId;
  private @Nullable Icon myIcon;
  private @Nullable @NlsContexts.NotificationSubtitle String mySubtitle;
  private @Nullable Boolean myIsImportant;
  private @Nullable @NlsContexts.LinkLabel String myDropDownText;
  private @Nullable List<AnAction> myActions;
  private @Nullable AnAction myContextHelpAction;
  private @Nullable Runnable myWhenExpired;

  /**
   * @param groupId notification group id registered in plugin.xml {@link com.intellij.notification.impl.NotificationGroupEP}
   */
  public NotificationBuilder(@NotNull String groupId,
                             @NotNull @NlsContexts.NotificationContent String content,
                             @NotNull NotificationType type) {
    this(groupId, "", content, type);
  }

  /**
   * @param groupId notification group id registered in plugin.xml {@link com.intellij.notification.impl.NotificationGroupEP}
   */
  public NotificationBuilder(@NotNull String groupId,
                             @NotNull @NlsContexts.NotificationTitle String title,
                             @NotNull @NlsContexts.NotificationContent String content,
                             @NotNull NotificationType type) {
    if (NotificationGroupManager.getInstance().getNotificationGroup(groupId) == null) {
      throw new IllegalArgumentException("Notification group `" + groupId + "` is not registered in plugin.xml file");
    }
    myTitle = title;
    myGroupId = groupId;
    myType = type;
    myContent = content;
  }

  public @NotNull NotificationBuilder setTitle(@NotNull @NlsContexts.NotificationTitle String title) {
    myTitle = title;
    return this;
  }

  public @NotNull NotificationBuilder setContent(@NotNull @NlsContexts.NotificationContent String content) {
    myContent = content;
    return this;
  }

  public @NotNull NotificationBuilder setType(@NotNull NotificationType type) {
    myType = type;
    return this;
  }

  public @NotNull NotificationBuilder setListener(@Nullable NotificationListener listener) {
    myListener = listener;
    return this;
  }

  public @NotNull NotificationBuilder setNotificationId(@Nullable @NonNls String notificationId) {
    myNotificationId = notificationId;
    return this;
  }

  public @NotNull NotificationBuilder setImportant(@Nullable Boolean important) {
    myIsImportant = important;
    return this;
  }

  public @NotNull NotificationBuilder setIcon(@Nullable Icon icon) {
    myIcon = icon;
    return this;
  }

  public @NotNull NotificationBuilder setSubtitle(@NlsContexts.NotificationSubtitle @Nullable String subtitle) {
    mySubtitle = subtitle;
    return this;
  }

  public @NotNull NotificationBuilder setDropDownText(@NotNull @NlsContexts.LinkLabel String dropDownText) {
    myDropDownText = dropDownText;
    return this;
  }

  public @NotNull NotificationBuilder addAction(@NotNull AnAction action) {
    if (myActions == null) {
      myActions = new ArrayList<>();
    }
    myActions.add(action);
    return this;
  }

  public @NotNull NotificationBuilder setContextHelpAction(@Nullable AnAction contextHelpAction) {
    myContextHelpAction = contextHelpAction;
    return this;
  }

  public @NotNull NotificationBuilder whenExpired(@Nullable Runnable whenExpired) {
    myWhenExpired = whenExpired;
    return this;
  }

  public @NotNull Notification build() {
    Notification notification = new Notification(myGroupId, myTitle, myContent, myType)
      .setIcon(myIcon)
      .setSubtitle(mySubtitle)
      .whenExpired(myWhenExpired);
    if (myListener != null) notification.setListener(myListener);
    if (myNotificationId != null) notification.setDisplayId(myNotificationId);
    if (myDropDownText != null) notification.setDropDownText(myDropDownText);
    if (myActions != null) notification.addActions(myActions);
    if (myContextHelpAction != null) notification.setContextHelpAction(myContextHelpAction);
    if (myIsImportant != null) notification.setImportant(myIsImportant);
    return notification;
  }

  public @NotNull Notification buildAndNotify(@NotNull Project project) {
    Notification notification = build();
    notification.notify(project);
    return notification;
  }
}
