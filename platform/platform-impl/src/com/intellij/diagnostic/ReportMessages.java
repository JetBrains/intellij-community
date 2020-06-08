// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author: Eugene Zhuravlev
 */
package com.intellij.diagnostic;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;

public final class ReportMessages {
  /**
   * @deprecated Use {@link #getErrorReport()} instead
   */
  @Deprecated
  public static final String ERROR_REPORT = "Error Report";

  public static String getErrorReport() {
    return DiagnosticBundle.message("error.report.title");
  }

  public static final NotificationGroup GROUP =
    new NotificationGroup("Error Report", NotificationDisplayType.BALLOON, false, getErrorReport());
}
