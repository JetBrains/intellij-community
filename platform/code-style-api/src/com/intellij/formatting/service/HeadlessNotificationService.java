// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
class HeadlessNotificationService implements FormattingNotificationService {
  static final HeadlessNotificationService INSTANCE = new HeadlessNotificationService();

  @Override
  public void reportError(@NotNull String groupId, @NotNull String title, @NotNull String message) {
    System.err.println(title + ": " + message);
  }

  @Override
  public void reportErrorAndNavigate(@NotNull String groupId,
                                     @NotNull String title,
                                     @NotNull String message,
                                     @NotNull FormattingContext context,
                                     int offset) {
    reportError(groupId, title, message);
  }
}
