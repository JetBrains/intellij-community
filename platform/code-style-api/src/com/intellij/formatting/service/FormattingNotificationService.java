// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FormattingNotificationService {
  static @NotNull FormattingNotificationService getInstance(@NotNull Project project) {
    return project.getService(FormattingNotificationService.class);
  }

  default void reportError(@NotNull String groupId,
                           @NotNull @NlsContexts.NotificationTitle String title,
                           @NotNull @NlsContexts.NotificationContent String message) {
    reportError(groupId, null, title, message);
  }

  default void reportError(@NotNull String groupId,
                           @Nullable String displayId,
                           @NotNull @NlsContexts.NotificationTitle String title,
                           @NotNull @NlsContexts.NotificationContent String message) {
    reportError(groupId, displayId, title, message, AnAction.EMPTY_ARRAY);
  }

  default void reportError(@NotNull String groupId,
                           @NotNull @NlsContexts.NotificationTitle String title,
                           @NotNull @NlsContexts.NotificationContent String message, AnAction... actions) {
    reportError(groupId, null, title, message, actions);
  }

  void reportError(@NotNull String groupId,
                   @Nullable String displayId,
                   @NotNull @NlsContexts.NotificationTitle String title,
                   @NotNull @NlsContexts.NotificationContent String message, AnAction... actions);

  default void reportErrorAndNavigate(@NotNull String groupId,
                                      @NotNull @NlsContexts.NotificationTitle String title,
                                      @NotNull @NlsContexts.NotificationContent String message,
                                      @NotNull FormattingContext context,
                                      int offset) {
    reportErrorAndNavigate(groupId, null, title, message, context, offset);
  }

  void reportErrorAndNavigate(@NotNull String groupId,
                              @Nullable String displayId,
                              @NotNull @NlsContexts.NotificationTitle String title,
                              @NotNull @NlsContexts.NotificationContent String message,
                              @NotNull FormattingContext context,
                              int offset);
}
