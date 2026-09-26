// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.callback.OpenProjectJdkSettingsCallback;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class ExternalSystemNotificationExtensionImpl implements ExternalSystemNotificationExtension {
  @Override
  public @NotNull ProjectSystemId getTargetExternalSystemId() {
    return ProjectSystemId.IDE;
  }

  @Override
  public void customize(
    @NotNull NotificationData notification,
    @NotNull Project project,
    @NotNull String externalProjectPath,
    @Nullable Throwable error
  ) {
    if (error == null) return;
    Throwable unwrapped = RemoteUtil.unwrap(error);
    if (unwrapped instanceof ExternalSystemException) {
      updateNotification(notification, project, (ExternalSystemException)unwrapped);
    }
  }

  private static void updateNotification(final @NotNull NotificationData notificationData,
                                         final @NotNull Project project,
                                         @NotNull ExternalSystemException e) {

    for (String fix : e.getQuickFixes()) {
      if (OpenProjectJdkSettingsCallback.ID.equals(fix)) {
        notificationData.setListener(OpenProjectJdkSettingsCallback.ID, new OpenProjectJdkSettingsCallback(project));
      }
    }
  }
}
