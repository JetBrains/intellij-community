// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.notification.Notification;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlatformIdeServiceImpl extends PlatformIdeService {
  @Override
  public void notification(@NotNull String groupId,
                           @NotNull PlatformIdeService.NotificationType type,
                           @Nullable String title,
                           @Nullable String subtitle,
                           @NotNull String content,
                           @Nullable Project project,
                           @NotNull String displayId) {
    new Notification(groupId, content, com.intellij.notification.NotificationType.valueOf(type.name()))
      .setDisplayId(displayId)
      .setTitle(title, subtitle)
      .notify(project);
  }
}
