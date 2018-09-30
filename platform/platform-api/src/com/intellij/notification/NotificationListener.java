// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.ide.BrowserUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.net.URL;

/**
 * Consider using {@link NotificationAction} instead of links in HTML content.
 *
 * @see NotificationAction
 */
public interface NotificationListener {
  void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event);

  abstract class Adapter implements NotificationListener {
    @Override
    public final void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        hyperlinkActivated(notification, event);
      }
    }

    protected abstract void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e);
  }

  NotificationListener URL_OPENING_LISTENER = new UrlOpeningListener(true);

  class UrlOpeningListener extends Adapter {
    private final boolean myExpireNotification;

    public UrlOpeningListener(boolean expireNotification) {
      myExpireNotification = expireNotification;
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      URL url = event.getURL();
      if (url == null) {
        BrowserUtil.browse(event.getDescription());
      }
      else {
        BrowserUtil.browse(url);
      }
      if (myExpireNotification) {
        notification.expire();
      }
    }
  }
}