/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * Validates the given external executable.
 * If it is not valid, shows notification to fix it.
 * Notification balloon in the upper right corner is shown only the first time. After it is shown in the toolwindow group.
 * This is done, because it is hard to discover whether notification balloon is showing or not, and showing several balloons about a single
 * problem is a bad idea.
 *
 * @author Kirill Likhodedov
 */
public abstract class ExecutableValidator {

  public static final String NOTIFICATION_ID = "External Executable Critical Failures";
  private final NotificationGroup myNotificationGroup = new NotificationGroup(NOTIFICATION_ID, NotificationDisplayType.TOOL_WINDOW, true,
                                                                              ToolWindowId.VCS);

  private final Project myProject;
  private final String myNotificationErrorTitle;
  private final String myNotificationErrorDescription;

  /**
   * Configures notification and dialog by setting text messages and titles specific to the whoever uses the validator.
   * @param notificationErrorTitle       title of the notification about not valid executable.
   * @param notificationErrorDescription description of this notification with a link to fix it (link action is defined by
   *                          {@link #showSettingsAndExpireIfFixed(com.intellij.notification.Notification)}
   */
  public ExecutableValidator(Project project, String notificationErrorTitle, String notificationErrorDescription) {
    myProject = project;
    myNotificationErrorTitle = notificationErrorTitle;
    myNotificationErrorDescription = notificationErrorDescription;
  }

  /**
   * @return path to current executable.
   */
  protected abstract String getCurrentExecutable();

  /**
   * @return the settings configurable where the executable is shown and can be fixed.
   *         This configurable will be opened if user presses "Fix" on the notification about invalid executable.
   */
  @NotNull
  protected abstract Configurable getConfigurable();

  /**
   * Returns true if the supplied executable is valid.
   * Default implementation: try to execute the given executable and test if output returned errors.
   * This can take a long time since it spawns external process.
   * @param executable Path to executable.
   * @return true if process with the supplied executable completed without errors and with exit code 0.
   */
  protected boolean isExecutableValid(String executable) {
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(executable);
      CapturingProcessHandler handler = new CapturingProcessHandler(commandLine.createProcess(), CharsetToolkit.getDefaultSystemCharset());
      ProcessOutput result = handler.runProcess(60 * 1000);
      return !result.isTimeout() && (result.getExitCode() == 0) && result.getStderr().isEmpty();
    } catch (Throwable e) {
      return false;
    }
  }

  /**
   * Shows a notification about not configured executable with a link to the Settings to fix it.
   * Expires the notification if user fixes the path from the opened Settings dialog.
   * Makes sure that there is always only one notification about the problem in the stack of notifications.
   */
  private void showExecutableNotConfiguredNotification() {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }

    myNotificationGroup.createNotification("", prepareDescription(), NotificationType.ERROR,
      new NotificationListener() {
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if (event.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
            showSettingsAndExpireIfFixed(notification);
          }
        }
      }
    ).notify(myProject.isDefault() ? null : myProject);
  }
  
  @NotNull
  private String prepareDescription() {
    String executable = getCurrentExecutable();
    if (executable.isEmpty()) {
      return String.format("<b>%s</b>%s", myNotificationErrorTitle, myNotificationErrorDescription);
    } else {
      return String.format("<b>%s:</b> <code>%s</code><br/>%s", myNotificationErrorTitle, executable, myNotificationErrorDescription);
    }
  }

  private void showSettingsAndExpireIfFixed(@NotNull Notification notification) {
    Configurable configurable = getConfigurable();
    ShowSettingsUtil.getInstance().showSettingsDialog(myProject, configurable.getDisplayName());
    if (isExecutableValid(getCurrentExecutable())) {
      notification.expire();
    }
  }

  /**
   * Checks if executable is valid and displays the notification if not.
   * @return true if executable was valid, false - if not valid (and notification was shown in that case).
   */
  public boolean checkExecutableAndNotifyIfNeeded() {
    if (!isExecutableValid(getCurrentExecutable())) {
      showExecutableNotConfiguredNotification();
      return false;
    }
    return true;
  }
  
  public boolean isExecutableValid() {
    return isExecutableValid(getCurrentExecutable());
  }
  
}
