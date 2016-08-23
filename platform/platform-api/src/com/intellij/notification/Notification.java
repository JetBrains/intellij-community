/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class Notification {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.Notification");
  private static final DataKey<Notification> KEY = DataKey.create("Notification");

  public final String id;

  private final String myGroupId;
  private Icon myIcon;
  private final NotificationType myType;

  private String myTitle;
  private String mySubtitle;
  private String myContent;
  private NotificationListener myListener;
  private String myDropDownText;
  private List<AnAction> myActions;

  private boolean myExpired;
  private Runnable myWhenExpired;
  private Boolean myImportant;
  private WeakReference<Balloon> myBalloonRef;
  private final long myTimestamp;

  public Notification(@NotNull String groupDisplayId, @NotNull Icon icon, @NotNull NotificationType type) {
    this(groupDisplayId, icon, null, null, null, type, null);
  }

  public Notification(@NotNull String groupDisplayId,
                      @NotNull Icon icon,
                      @Nullable String title,
                      @Nullable String subtitle,
                      @Nullable String content,
                      @NotNull NotificationType type,
                      @Nullable NotificationListener listener) {
    myGroupId = groupDisplayId;
    myTitle = StringUtil.notNullize(title);
    myContent = StringUtil.notNullize(content);
    myType = type;
    myListener = listener;
    myTimestamp = System.currentTimeMillis();

    myIcon = icon;
    mySubtitle = subtitle;

    LOG.assertTrue(isTitle() || isContent(), "Notification should have title: " + title + " and/or subtitle and/or content groupId: " + myGroupId);

    id = String.valueOf(System.currentTimeMillis()) + "." + String.valueOf(System.identityHashCode(this));
  }

  public Notification(@NotNull String groupDisplayId, @NotNull String title, @NotNull String content, @NotNull NotificationType type) {
    this(groupDisplayId, title, content, type, null);
  }

  /**
   * @param groupDisplayId this should be a human-readable, capitalized string like "Facet Detector".
   *                       It will appear in "Notifications" configurable.
   * @param title          notification title
   * @param content        notification content
   * @param type           notification type
   * @param listener       notification lifecycle listener
   */
  public Notification(@NotNull String groupDisplayId,
                      @NotNull String title,
                      @NotNull String content,
                      @NotNull NotificationType type,
                      @Nullable NotificationListener listener) {
    myGroupId = groupDisplayId;
    myTitle = title;
    myContent = content;
    myType = type;
    myListener = listener;
    myTimestamp = System.currentTimeMillis();

    LOG.assertTrue(isContent(), "Notification should have content, title: " + title + ", groupId: " + myGroupId);
    id = String.valueOf(System.currentTimeMillis()) + "." + String.valueOf(hashCode());
  }

  /**
   * Returns the time (in milliseconds since Jan 1, 1970) when the notification was created.
   */
  public long getTimestamp() {
    return myTimestamp;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public Notification setIcon(@Nullable Icon icon) {
    myIcon = icon;
    return this;
  }

  @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  public boolean isTitle() {
    return !StringUtil.isEmptyOrSpaces(myTitle) || !StringUtil.isEmptyOrSpaces(mySubtitle);
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  public Notification setTitle(@Nullable String title) {
    myTitle = StringUtil.notNullize(title);
    return this;
  }

  @NotNull
  public Notification setTitle(@Nullable String title, @Nullable String subtitle) {
    return setTitle(title).setSubtitle(subtitle);
  }

  @Nullable
  public String getSubtitle() {
    return mySubtitle;
  }

  @NotNull
  public Notification setSubtitle(@Nullable String subtitle) {
    mySubtitle = subtitle;
    return this;
  }

  public boolean isContent() {
    return !StringUtil.isEmptyOrSpaces(myContent);
  }

  @NotNull
  public String getContent() {
    return myContent;
  }

  @NotNull
  public Notification setContent(@Nullable String content) {
    myContent = StringUtil.notNullize(content);
    return this;
  }

  @Nullable
  public NotificationListener getListener() {
    return myListener;
  }

  @NotNull
  public Notification setListener(@NotNull NotificationListener listener) {
    myListener = listener;
    return this;
  }

  @NotNull
  public List<AnAction> getActions() {
    return ContainerUtil.notNullize(myActions);
  }

  @NotNull
  public static Notification get(@NotNull AnActionEvent e) {
    //noinspection ConstantConditions
    return e.getData(KEY);
  }

  public static void fire(@NotNull final Notification notification, @NotNull AnAction action) {
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, new DataContext() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        return KEY.getName().equals(dataId) ? notification : null;
      }
    });
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAware(action, event);
    }
  }

  @NotNull
  public String getDropDownText() {
    if (myDropDownText == null) {
      myDropDownText = "Actions";
    }
    return myDropDownText;
  }

  @NotNull
  public Notification setDropDownText(@NotNull String dropDownText) {
    myDropDownText = dropDownText;
    return this;
  }

  @NotNull
  public Notification addAction(@NotNull AnAction action) {
    if (myActions == null) {
      myActions = new ArrayList<>();
    }
    myActions.add(action);
    return this;
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
    hideBalloon();
    myExpired = true;

    Runnable whenExpired = myWhenExpired;
    if (whenExpired != null) whenExpired.run();
  }

  public Notification whenExpired(@Nullable Runnable whenExpired) {
    myWhenExpired = whenExpired;
    return this;
  }

  public void hideBalloon() {
    if (myBalloonRef != null) {
      final Balloon balloon = myBalloonRef.get();
      if (balloon != null) {
        balloon.hide();
      }
      myBalloonRef = null;
    }
  }

  public void setBalloon(@NotNull final Balloon balloon) {
    hideBalloon();
    myBalloonRef = new WeakReference<>(balloon);
    balloon.addListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        if (SoftReference.dereference(myBalloonRef) == balloon) {
          myBalloonRef = null;
        }
      }
    });
  }

  @Nullable
  public Balloon getBalloon() {
    return SoftReference.dereference(myBalloonRef);
  }

  public void notify(@Nullable Project project) {
    Notifications.Bus.notify(this, project);
  }

  public Notification setImportant(boolean important) {
    myImportant = important;
    return this;
  }

  public boolean isImportant() {
    if (myImportant != null) {
      return myImportant;
    }

    return getListener() != null || !ContainerUtil.isEmpty(myActions);
  }
}
