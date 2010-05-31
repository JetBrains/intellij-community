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
import com.intellij.openapi.ui.popup.Balloon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;

/**
 * @author spleaner
 */
public class Notification {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.Notification");

  private String myGroupId;
  private String myContent;
  private NotificationType myType;
  private NotificationListener myListener;
  private String myTitle;
  private boolean myExpired;
  private WeakReference<Balloon> myBalloonRef;

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
    LOG.assertTrue(myContent.trim().length() > 0, "Notification should have content!");
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
    if (myBalloonRef != null) {
      final Balloon balloon = myBalloonRef.get();
      if (balloon != null) {
        balloon.hide();
      }
      myBalloonRef = null;
    }
    myExpired = true;
  }

  public void setBalloon(@Nullable final Balloon balloon) {
    if (balloon != null) {
      myBalloonRef = new WeakReference<Balloon>(balloon);
    } else {
      myBalloonRef = null;
    }
  }

  @Nullable
  public Balloon getBalloon() {
    return myBalloonRef == null ? null : myBalloonRef.get();
  }
}
