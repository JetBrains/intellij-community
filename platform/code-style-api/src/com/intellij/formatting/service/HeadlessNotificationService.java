// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
class HeadlessNotificationService implements FormattingNotificationService {
  final static HeadlessNotificationService INSTANCE = new HeadlessNotificationService();

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
