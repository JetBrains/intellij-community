// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import org.jetbrains.annotations.Nls;

public final class ReportMessages {
  private ReportMessages() { }

  /** @deprecated Please use {@code DiagnosticBundle.message("error.report.title")} instead. */
  @Deprecated(forRemoval = true)
  public static final String ERROR_REPORT = "Error Report";

  /** @deprecated Please use {@code DiagnosticBundle.message("error.report.title")} instead. */
  @Deprecated(forRemoval = true)
  public static @Nls String getErrorReport() {
    return DiagnosticBundle.message("error.report.title");
  }

  /** @deprecated use {@code new Notification("Error Report", ...)} instead */
  @Deprecated(forRemoval = true)
  public static final NotificationGroup GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Error Report");
}
