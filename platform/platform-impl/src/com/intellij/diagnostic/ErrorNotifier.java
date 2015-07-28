/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

/**
 * @author peter
 */
public class ErrorNotifier {
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("IDE Fatal Errors");

  public static void notifyUi(final LogMessage message, final MessagePool pool) {
    //noinspection ThrowableResultOfMethodCallIgnored
    if (message.getThrowable() instanceof MessagePool.TooManyErrorsException) {
      NOTIFICATION_GROUP.createNotification(message.getMessage(), NotificationType.ERROR).notify(null);
      return;
    }

    String title = "<a href='xxx'>" + getTitle(message) + "</a>";
    String notificationText = getNotificationText(message);
    NotificationListener listener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        openFatals(event, message);
      }
    };
    Notification notification = new Notification(NOTIFICATION_GROUP.getDisplayId(), title, notificationText, NotificationType.ERROR, listener) {
        @Override
        public void expire() {
          super.expire();
          if (!message.isRead()) {
            message.setRead(true);
          }
          pool.notifyListenersRead();
        }
      };
    notification.notify(null);
    message.setNotification(notification);
  }

  private static void openFatals(HyperlinkEvent event, LogMessage message) {
    Object source = event.getSource();
    if (source instanceof Component) {
      Window window = SwingUtilities.getWindowAncestor((Component)source);
      if (window instanceof IdeFrame) {
        final StatusBar statusBar = ((IdeFrame)window).getStatusBar();
        StatusBarWidget widget = statusBar == null ? null : statusBar.getWidget(IdeMessagePanel.FATAL_ERROR);
        if (widget instanceof IdeMessagePanel) {
          ((IdeMessagePanel)widget).openFatals(message);
        }
      }
    }
  }

  private static String getNotificationText(LogMessage message) {
    String text = message.getMessage();
    if (message instanceof LogMessageEx) {
      String result = ((LogMessageEx)message).getNotificationText();
      if (result != null) {
        text = StringUtil.stripHtml(result, false);
      }
    }
    return text;
  }

  private static String getTitle(LogMessage message) {
    if (message instanceof LogMessageEx) {
      return ((LogMessageEx)message).getTitle();
    }
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored") Throwable throwable = message.getThrowable();
    return throwable == null ? "IDE Fatal Error" : throwable.getClass().getSimpleName();
  }

}
