/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.jshell;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

/**
 * @author Eugene Zhuravlev
 * Date: 04-Jul-17
 */
public class JShellDiagnostic {
  private static final String NOTIFICATION_GROUP = "JSHELL_NOTIFICATIONS";
  private static final String TITLE = "JShell";

  public static void notifyInfo(final String text, final Project project) {
    new Notification(NOTIFICATION_GROUP, TITLE, text, NotificationType.INFORMATION).notify(project);
  }

  public static void notifyError(Exception ex, final Project project) {
    new Notification(NOTIFICATION_GROUP, TITLE, ex.getMessage(), NotificationType.ERROR).notify(project);
  }
}
