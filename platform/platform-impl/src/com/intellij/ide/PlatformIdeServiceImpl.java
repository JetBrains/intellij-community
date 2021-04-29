// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.net.URL;

public class PlatformIdeServiceImpl extends PlatformIdeService {
  @Override
  public void warningNotification(@NotNull @NonNls String groupId,
                                  @Nullable Icon icon,
                                  @Nullable @NlsContexts.NotificationTitle String title,
                                  @Nullable @NlsContexts.NotificationSubtitle String subtitle,
                                  @Nullable @NlsContexts.NotificationContent String content) {
    notification(groupId, icon, title, subtitle, content, NotificationType.WARNING);
  }

  private static void notification(@NonNls @NotNull String groupId,
                                   @Nullable Icon icon,
                                   @Nullable @NlsContexts.NotificationTitle String title,
                                   @Nullable @NlsContexts.NotificationSubtitle String subtitle,
                                   @Nullable @NlsContexts.NotificationContent String content, @NotNull NotificationType notificationType) {
    Notification notification = new Notification(groupId, icon, title, subtitle, content, notificationType, null);
    Notifications.Bus.notify(notification);
  }

  @Override
  public void browseHyperlinkEvent(HyperlinkEvent event) {
    URL url = event.getURL();
    if (url == null) {
      BrowserUtil.browse(event.getDescription());
    }
    else {
      BrowserUtil.browse(url);
    }
  }
}
