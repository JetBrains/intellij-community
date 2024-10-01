// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.function.BiConsumer;

@ApiStatus.Internal
public final class PlatformIdeServiceImpl extends PlatformIdeService {
  @Override
  public @NotNull BiConsumer<Object, HyperlinkEvent> createHyperlinkConsumer() {
    NotificationListener.UrlOpeningListener res = new NotificationListener.UrlOpeningListener(false);
    return (o, event) -> {
      if (o instanceof Notification n) res.hyperlinkUpdate(n, event);
    };
  }

  @Override
  public void notification(@NotNull String groupId,
                           @NotNull NotificationType type,
                           @Nullable String title,
                           @Nullable String subtitle,
                           @NotNull String content,
                           @Nullable Project project,
                           @NotNull String displayId,
                           @Nullable BiConsumer<Object, HyperlinkEvent> listener) {
    Notification notification = new Notification(groupId, content, com.intellij.notification.NotificationType.valueOf(type.name()))
      .setDisplayId(displayId)
      .setTitle(title, subtitle);
    if (listener != null) {
      notification.setListener((n, event) -> listener.accept(n, event));
    }
    notification.notify(project);
  }
}
