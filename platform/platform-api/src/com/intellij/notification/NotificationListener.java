/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.BrowserUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.net.URL;

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