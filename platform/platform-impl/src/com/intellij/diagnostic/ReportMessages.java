// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

public final class ReportMessages {
  /** @deprecated Please use {@code DiagnosticBundle.message("error.report.title")} instead. */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static final String ERROR_REPORT = "Error Report";

  /** @deprecated Please use {@code DiagnosticBundle.message("error.report.title")} instead. */
  @Deprecated
  public static @Nls String getErrorReport() {
    return DiagnosticBundle.message("error.report.title");
  }

  /** @deprecated Use {@code NotificationGroupManager.getInstance().getNotificationGroup("Error Report")} instead */
  @Deprecated
  public static final NotificationGroup GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Error Report");
}
