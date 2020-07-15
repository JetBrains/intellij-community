// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jshell;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author Eugene Zhuravlev
 */
public final class JShellDiagnostic {
  private static final String NOTIFICATION_GROUP = "JSHELL_NOTIFICATIONS";
  private static final String TITLE = "JShell";

  public static void notifyInfo(final String text, final Project project) {
    new Notification(NOTIFICATION_GROUP, TITLE, text, NotificationType.INFORMATION).notify(project);
  }

  public static void notifyError(Exception ex, final Project project) {
    new Notification(NOTIFICATION_GROUP, TITLE, StringUtil.notNullize(ex.getMessage(), "internal error"), NotificationType.ERROR).notify(project);
  }
}