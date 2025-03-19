// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.util.NlsContexts.NotificationTitle;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.pom.NonNavigatable;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public class NotificationData implements Disposable {

  private @NotNull @NotificationTitle String myTitle;
  private @NotNull @NotificationContent String myMessage;
  private @NotNull NotificationCategory myNotificationCategory;
  private final @NotNull NotificationSource myNotificationSource;
  private final @NotNull NotificationListener myListener;
  private @Nullable String myFilePath;
  private @Nullable Navigatable navigatable;
  private int myLine;
  private int myColumn;
  private boolean myBalloonNotification;
  private @Nullable NotificationGroup myBalloonGroup;
  private boolean myIsSuggestion;

  private final Map<String, NotificationListener> myListenerMap;

  public NotificationData(@NotNull @NotificationTitle String title,
                          @NotNull @NotificationContent String message,
                          @NotNull NotificationCategory notificationCategory,
                          @NotNull NotificationSource notificationSource) {
    this(title, message, notificationCategory, notificationSource, null, -1, -1, false);
  }

  public NotificationData(@NotNull @NotificationTitle String title,
                          @NotNull @NotificationContent String message,
                          @NotNull NotificationCategory notificationCategory,
                          @NotNull NotificationSource notificationSource,
                          @Nullable String filePath,
                          int line,
                          int column,
                          boolean balloonNotification) {
    myTitle = title;
    myMessage = message;
    myNotificationCategory = notificationCategory;
    myNotificationSource = notificationSource;
    myListenerMap = new HashMap<>();
    myListener = new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;

        final NotificationListener notificationListener = myListenerMap.get(event.getDescription());
        if (notificationListener != null) {
          notificationListener.hyperlinkUpdate(notification, event);
        }
      }
    };
    myFilePath = filePath;
    myLine = line;
    myColumn = column;
    myBalloonNotification = balloonNotification;
  }

  public @NotNull @NotificationTitle String getTitle() {
    return myTitle;
  }

  public void setTitle(@NotNull @NotificationTitle String title) {
    myTitle = title;
  }

  public @NotNull @NotificationContent String getMessage() {
    return myMessage;
  }

  public void setMessage(@NotNull @NotificationContent String message) {
    myMessage = message;
  }

  public @NotNull NotificationCategory getNotificationCategory() {
    return myNotificationCategory;
  }

  public void setNotificationCategory(@NotNull NotificationCategory notificationCategory) {
    myNotificationCategory = notificationCategory;
  }

  public @NotNull NotificationSource getNotificationSource() {
    return myNotificationSource;
  }

  public @NotNull NotificationListener getListener() {
    return myListener;
  }

  public @Nullable String getFilePath() {
    return myFilePath;
  }

  public void setFilePath(@Nullable String filePath) {
    myFilePath = filePath;
  }

  public @NotNull Integer getLine() {
    return myLine;
  }

  public void setLine(int line) {
    myLine = line;
  }

  public int getColumn() {
    return myColumn;
  }

  public void setColumn(int column) {
    myColumn = column;
  }

  public boolean isBalloonNotification() {
    return myBalloonNotification;
  }

  public void setBalloonNotification(boolean balloonNotification) {
    myBalloonNotification = balloonNotification;
  }

  public void setListener(@NotNull String listenerId, @NotNull NotificationListener listener) {
    myListenerMap.put(listenerId, listener);
  }

  boolean hasLinks() {
    return !myListenerMap.isEmpty();
  }

  public List<String> getRegisteredListenerIds() {
    return new ArrayList<>(myListenerMap.keySet());
  }

  public @Nullable Navigatable getNavigatable() {
    if (navigatable == null || navigatable == NonNavigatable.INSTANCE) {
      for (String id : myListenerMap.keySet()) {
        if (id.startsWith("openFile:")) {
          return new NavigatableAdapter() {
            @Override
            public void navigate(boolean requestFocus) {
              NotificationListener listener = myListenerMap.get(id);
              if (listener != null) {
                // Notification here used only to be able to call 'NotificationListener.hyperlinkUpdate'
                //noinspection UnresolvedPluginConfigReference
                listener.hyperlinkUpdate(new Notification("", "", NotificationType.INFORMATION),
                                         IJSwingUtilities.createHyperlinkEvent(id, listener));
              }
            }
          };
        }
      }
    }
    return navigatable;
  }

  public void setNavigatable(@Nullable Navigatable navigatable) {
    this.navigatable = navigatable;
  }

  public @Nullable NotificationGroup getBalloonGroup() {
    return myBalloonGroup;
  }

  public void setBalloonGroup(NotificationGroup balloonGroup) {
    myBalloonGroup = balloonGroup;
  }

  @Override
  public void dispose() {
    myListenerMap.clear();
  }
}
