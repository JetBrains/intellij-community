// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link FormattingNotificationService} instead.
 */
@Deprecated(forRemoval = true)
final class FormattingUiNotificationService implements FormattingNotificationService {

  private final @NotNull Project myProject;

  FormattingUiNotificationService(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void reportError(@NotNull String groupId,
                          @Nullable String displayId,
                          @NotNull @NlsContexts.NotificationTitle String title,
                          @NotNull @NlsContexts.NotificationContent String message, AnAction... actions) {
    FormattingNotificationUtil.reportError(myProject, groupId, displayId, title, message, actions);
  }

  @Override
  public void reportErrorAndNavigate(@NotNull String groupId,
                                     @Nullable String displayId,
                                     @NotNull String title,
                                     @NotNull String message,
                                     @NotNull FormattingContext context,
                                     int offset) {
    FormattingNotificationUtil.reportErrorAndNavigate(myProject, groupId, displayId, title, message, context, offset);
  }
}
