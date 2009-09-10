package com.intellij.notification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class Notification {
  private String myGroupId;
  private String myContent;
  private NotificationType myType;
  private NotificationListener myListener;
  private String myTitle;
  private boolean myExpired;

  public Notification(@NotNull final String groupId, @NotNull final String title, @NotNull final String content, @NotNull final NotificationType type) {
    myGroupId = groupId;
    myTitle = title;
    myContent = content;
    myType = type;
  }

  public Notification(@NotNull final String groupId, @NotNull final String title, @NotNull final String content, @NotNull final NotificationType type, @Nullable NotificationListener listener) {
    this(groupId, title, content, type);
    myListener = listener;
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  public String getTitle() {
    return myTitle;
  }

  @NotNull
  public String getContent() {
    return myContent;
  }

  @Nullable
  public NotificationListener getListener() {
    return myListener;
  }

  @NotNull
  public NotificationType getType() {
    return myType;
  }

  public boolean isExpired() {
    return myExpired;
  }

  public void expire() {
    NotificationsManager.getNotificationsManager().expire(this);
    myExpired = true;
  }
}
