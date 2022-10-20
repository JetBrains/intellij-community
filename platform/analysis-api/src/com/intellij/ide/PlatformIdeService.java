// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PlatformIdeService {
  public static PlatformIdeService getInstance() {
    return ApplicationManager.getApplication().getService(PlatformIdeService.class);
  }

  /** @implNote should be a strict subset of {@link com.intellij.notification.NotificationType} */
  public enum NotificationType {INFORMATION, WARNING, ERROR}

  public void notification(@NotNull String groupId,
                           @NotNull NotificationType type,
                           @Nullable @NlsContexts.NotificationTitle String title,
                           @Nullable @NlsContexts.NotificationSubtitle String subtitle,
                           @NotNull @NlsContexts.NotificationContent String content,
                           @Nullable Project project,
                           @NotNull String displayId) {
    StringBuilder message = new StringBuilder();
    if (title != null && !title.isEmpty()) {
      message.append(title);
      if (subtitle != null && !subtitle.isEmpty()) {
        message.append(" [").append(subtitle).append(']');
      }
      message.append(": ");
    }
    message.append(content);
    switch (type) {
      case ERROR -> Logger.getInstance(PlatformIdeService.class).error(message.toString());
      case WARNING -> Logger.getInstance(PlatformIdeService.class).warn(message.toString());
      case INFORMATION -> Logger.getInstance(PlatformIdeService.class).info(message.toString());
    }
  }
}
