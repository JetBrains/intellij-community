// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link FormattingNotificationService}
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("UseOfSystemOutOrSystemErr")
class HeadlessNotificationService implements FormattingNotificationService {
  static final HeadlessNotificationService INSTANCE = new HeadlessNotificationService();

  @Override
  public void reportError(@NotNull String groupId,
                          @Nullable String displayId,
                          @NotNull String title,
                          @NotNull String message,
                          AnAction... actions) {
    System.err.println(title + ": " + message);
  }


  @Override
  public void reportErrorAndNavigate(@NotNull String groupId,
                                     @Nullable String displayId,
                                     @NotNull String title,
                                     @NotNull String message,
                                     @NotNull FormattingContext context,
                                     int offset) {
    reportError(groupId, displayId, title, message);
  }
}
