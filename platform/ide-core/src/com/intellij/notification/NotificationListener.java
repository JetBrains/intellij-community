// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.ide.ui.IdeUiService;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.net.URL;

/**
 * Consider using {@link NotificationAction} instead of "action" links in HTML content.
 * Use {@link #URL_OPENING_LISTENER} to open external links in browser.
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
      if (url != null) {
        IdeUiService.getInstance().browse(url);
      }
      else {
        IdeUiService.getInstance().browse(event.getDescription());
      }

      if (myExpireNotification) {
        notification.expire();
      }
    }
  }
}
