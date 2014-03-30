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

import com.intellij.CommonBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

import static com.intellij.notification.NotificationDisplayType.STICKY_BALLOON;

/**
 * Validates the given external executable. If it is not valid, shows notification to fix it.
 * Notification group is registered as a {@link STICKY_BALLOON} by default.
 *
 * @author Kirill Likhodedov
 */
public abstract class ExecutableValidator {

  private static final Logger LOG = Logger.getInstance(ExecutableValidator.class);

  private static final NotificationGroup ourNotificationGroup = new NotificationGroup("External Executable Critical Failures",
                                                                              STICKY_BALLOON, true);
  @NotNull protected final Project myProject;
  @NotNull private final NotificationsManager myNotificationManager;

  @NotNull private final String myNotificationErrorTitle;
  @NotNull private String myNotificationErrorDescription;

  /**
   * Configures notification and dialog by setting text messages and titles specific to the whoever uses the validator.
   * @param notificationErrorTitle       title of the notification about not valid executable.
   * @param notificationErrorDescription description of this notification with a link to fix it (link action is defined by
   *                                     {@link #showSettingsAndExpireIfFixed(com.intellij.notification.Notification)}
   */
  public ExecutableValidator(@NotNull Project project, @NotNull String notificationErrorTitle,
                             @NotNull String notificationErrorDescription) {
    myProject = project;
    myNotificationErrorTitle = notificationErrorTitle;
    myNotificationErrorDescription = notificationErrorDescription;
    myNotificationManager = NotificationsManager.getNotificationsManager();
  }

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
  protected boolean isExecutableValid(@NotNull String executable) {
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(executable);
      CapturingProcessHandler handler = new CapturingProcessHandler(commandLine.createProcess(), CharsetToolkit.getDefaultSystemCharset());
      ProcessOutput result = handler.runProcess(60 * 1000);
      return !result.isTimeout() && (result.getExitCode() == 0) && result.getStderr().isEmpty();
    }
    catch (Throwable ignored) {
      return false;
    }
  }

  public void setNotificationErrorDescription(@NotNull String notificationErrorDescription) {
    myNotificationErrorDescription = notificationErrorDescription;
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

    LOG.info("Executable is not valid: " + getCurrentExecutable());
    if (myNotificationManager.getNotificationsOfType(ExecutableNotValidNotification.class, myProject).length == 0) { // show only once
      new ExecutableNotValidNotification().notify(myProject.isDefault() ? null : myProject);
    }
  }
  
  @NotNull
  private String prepareDescription() {
    String executable = getCurrentExecutable();
    if (executable.isEmpty()) {
      return String.format("<b>%s</b>%s <a href=''>Fix it.</a>", myNotificationErrorTitle, myNotificationErrorDescription);
    }
    else {
      return String.format("<b>%s:</b> <code>%s</code><br/>%s <a href=''>Fix it.</a>",
                           myNotificationErrorTitle, executable, myNotificationErrorDescription);
    }
  }

  protected void showSettingsAndExpireIfFixed(@NotNull Notification notification) {
    showSettings();
    if (isExecutableValid(getCurrentExecutable())) {
      notification.expire();
    }
  }

  protected void showSettings() {
    Configurable configurable = getConfigurable();
    ShowSettingsUtil.getInstance().showSettingsDialog(myProject, configurable.getDisplayName());
  }

  /**
   * Checks if executable is valid and displays the notification if not.
   * @return true if executable was valid, false - if not valid (and notification was shown in that case).
   * @see #checkExecutableAndShowMessageIfNeeded(java.awt.Component)
   */
  public boolean checkExecutableAndNotifyIfNeeded() {
    if (myProject.isDisposed()) {
      return false;
    }
    if (!isExecutableValid(getCurrentExecutable())) {
      showExecutableNotConfiguredNotification();
      return false;
    }
    return true;
  }

  /**
   * Checks if executable is valid and shows the message if not.
   * This method is to be used instead of {@link #checkExecutableAndNotifyIfNeeded()} when Git fails to start from a modal dialog:
   * in that case user won't be able to click "Fix it".
   * @return true if executable was valid, false - if not valid (and a message is shown in that case).
   * @see #checkExecutableAndNotifyIfNeeded()
   */
  public boolean checkExecutableAndShowMessageIfNeeded(@Nullable Component parentComponent) {
    if (myProject.isDisposed()) {
      return false;
    }

    if (!isExecutableValid(getCurrentExecutable())) {
      if (Messages.OK == showMessage(parentComponent)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            showSettings();
          }
        });
      }
      return false;
    }
    return true;
  }

  private int showMessage(@Nullable Component parentComponent) {
    String okText = "Fix it";
    String cancelText = CommonBundle.getCancelButtonText();
    Icon icon = Messages.getErrorIcon();
    String title = myNotificationErrorTitle;
    String description = myNotificationErrorDescription;
    return parentComponent != null
           ? Messages.showOkCancelDialog(parentComponent, description, title, okText, cancelText, icon)
           : Messages.showOkCancelDialog(myProject, description, title, okText, cancelText, icon);
  }

  public boolean isExecutableValid() {
    return isExecutableValid(getCurrentExecutable());
  }

  private class ExecutableNotValidNotification extends Notification {
    private ExecutableNotValidNotification() {
      super(ourNotificationGroup.getDisplayId(), "", prepareDescription(), NotificationType.ERROR, new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          showSettingsAndExpireIfFixed(notification);
        }
      });
    }
  }
}