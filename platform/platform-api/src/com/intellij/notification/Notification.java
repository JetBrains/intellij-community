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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;

/**
 * @author spleaner
 */
public class Notification {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.Notification");

  private final String myGroupId;
  private final String myContent;
  private final NotificationType myType;
  private final NotificationListener myListener;
  private final String myTitle;
  private boolean myExpired;
  private Runnable myWhenExpired;
  private Boolean myImportant;
  private WeakReference<Balloon> myBalloonRef;

  public Notification(@NotNull final String groupDisplayId, @NotNull final String title, @NotNull final String content, @NotNull final NotificationType type) {
    this(groupDisplayId, title, content, type, null);
  }

  /**
   * @param groupDisplayId this should be a human-readable, capitalized string like "Facet Detector".
   *                       It will appear in "Notifications" configurable.
   * @param title notification title
   * @param content notification content
   * @param type notification type
   * @param listener notification lifecycle listener
   */
  public Notification(@NotNull final String groupDisplayId, @NotNull final String title, @NotNull final String content, @NotNull final NotificationType type, @Nullable NotificationListener listener) {
    myGroupId = groupDisplayId;
    myTitle = title;
    myContent = content;
    myType = type;
    myListener = listener;

    LOG.assertTrue(myContent.trim().length() > 0, "Notification should have content, groupId: " + myGroupId);
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public Icon getIcon() {
    return null;
  }

  @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  @NotNull
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
    myBalloonRef = new WeakReference<Balloon>(balloon);
    balloon.addListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        WeakReference<Balloon> ref = myBalloonRef;
        if (ref != null && ref.get() == balloon) {
          myBalloonRef = null;
        }
      }
    });
  }

  @Nullable
  public Balloon getBalloon() {
    return myBalloonRef == null ? null : myBalloonRef.get();
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

    return getListener() != null;
  }
}
