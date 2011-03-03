/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.util;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

/**
 * Validates the supplied external executable.
 * Shows notification or a dialog to fix it.
 * @author Kirill Likhodedov
 */
public abstract class ExecutableValidator {

  private Notification myNotification;
  protected final Project myProject;
  private final String myNotificationGroupId;

  private String myNotificationErrorTitle = "Executable not valid";
  private String myNotificationErrorDescription = "You haven't configured a valid executable. <a href=''>Fix</a>";
  private String myDialogTitle = "Executable";
  private String myDialogDescription = "Specify the full path to the executable";
  private String myDialogErrorText = "It doesn't appear to be a valid executable";
  private String myFileChooserTitle = "Executable";
  private String myFileChooserDescription = "Specify the full path to the executable";

  public ExecutableValidator(Project project, String notificationGroupId) {
    myProject = project;
    myNotificationGroupId = notificationGroupId;
  }

  /**
   * Configures notification and dialog by setting text messages and titles specific to the whoever uses the validator.
   * @param notificationErrorTitle       title of the notification about not valid executable.
   * @param notificationErrorDescription description of this notification with a link to fix it (link action is defined by
   *                          {@link #notificationHyperlinkUpdate(com.intellij.notification.Notification, javax.swing.event.HyperlinkEvent)}
   * @param dialogTitle
   * @param dialogDescription
   * @param dialogErrorText
   * @param fileChooserTitle
   */
  public void setMessagesAndTitles(String notificationErrorTitle, String notificationErrorDescription,
                                   String dialogTitle, String dialogDescription, String dialogErrorText,
                                   String fileChooserTitle, String fileChooserDescription) {
    myNotificationErrorTitle = notificationErrorTitle;
    myNotificationErrorDescription = notificationErrorDescription;
    myDialogTitle = dialogTitle;
    myDialogDescription = dialogDescription;
    myDialogErrorText = dialogErrorText;
    myFileChooserTitle = fileChooserTitle;
    myFileChooserDescription = fileChooserDescription;
  }

  /**
   * Returns current executable persisted in the settings or elsewhere.
   * @return Path to current executable.
   */
  protected abstract String getCurrentExecutable();

  /**
   * Override this to save new (correct) executable path entered in the dialog.
   * @param executable
   */
  protected void saveCurrentExecutable(String executable) {
  }

  /**
   * Returns the configurable page for the vcs containing settings for executable.
   * This configurable will be opened if user presses "Fix" on the notification about invalid executable.
   * May return null - in this case the settings dialog won't be displayed.
   */
  @Nullable
  protected Configurable getConfigurable(Project project) {
    return null;
  }

  /**
   * Returns true if the supplied executable is valid.
   * Default implementation: try to execute the given executable and test if output returned errors.
   * @param executable Path to executable.
   * @return true if process with the supplied executable completed without errors and with exit code 0.
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  public boolean isExecutableValid(String executable) {
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

  public boolean showDialog() {
    final ExecutableDialog dialog = new ExecutableDialog(myProject, this);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        dialog.show();
      }
    });
    if (dialog.isOK()) {
      saveCurrentExecutable(dialog.getPath());
      return true;
    } else { // user pressed cancel
      showExecutableNotConfiguredNotification();
      return false;
    }
  }

  public boolean checkExecutableAndShowDialogIfNeeded() {
    if (!isExecutableValid(getCurrentExecutable())) {
      return showDialog();
    }
    return true;
  }

  /**
   * Shows a notification about not configured executable with a link to the Settings to fix it.
   * Expires the notification if user fixes the path from the opened Settings dialog.
   * Makes sure that there is always only one notification about the problem in the stack of notifications.
   */
  public void showExecutableNotConfiguredNotification() {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }

    final Notification newNotification = new Notification(myNotificationGroupId, myNotificationErrorTitle,
      myNotificationErrorDescription, NotificationType.ERROR,
      new NotificationListener() {
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          notificationHyperlinkUpdate(notification, event);
        }
      });

    // expire() needs to be called from AWT thread.
    // we also want to be sure that previous notification expires before new one is shown (and assigned to myNotification).
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (myNotification != null && !myNotification.isExpired()) {
          // don't store this notification twice, but redisplay it again so that popup appears.
          myNotification.expire();
        }
        myNotification = newNotification;
        Notifications.Bus.notify(myNotification, myProject.isDefault() ? null : myProject);
      }
    });
  }

  /**
   * The action which will be executed when the user presses "Fix" link in the notification description.
   * By default it opens the Settings dialog on the page correspondent to the supplied configurable (e.g. Git configurable)
   * and expires the notification after dialog is closed if executable was fixed in that dialog.
   * One may override the method.
   * Parameters are the same as in {@link com.intellij.notification.NotificationListener#hyperlinkUpdate(com.intellij.notification.Notification, javax.swing.event.HyperlinkEvent)}
   */
  protected void notificationHyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
    Configurable configurable = getConfigurable(myProject);
    if (configurable != null) {
      ShowSettingsUtil.getInstance().showSettingsDialog(myProject, configurable);
      if (isExecutableValid(getCurrentExecutable())) {
        notification.expire();
      }
    }
  }

  /**
   * Checks if executable is valid and displays the notification if not.
   * @return true if executable was valid, false - if not valid (and notification is shown in that case).
   */
  public boolean checkExecutableAndNotifyIfNeeded() {
    if (!isExecutableValid(getCurrentExecutable())) {
      showExecutableNotConfiguredNotification();
      return false;
    }
    return true;
  }

  String getDialogTitle() {
    return myDialogTitle;
  }

  String getDialogErrorText() {
    return myDialogErrorText;
  }

  String getFileChooserDescription() {
    return myFileChooserDescription;
  }

  String getFileChooserTitle() {
    return myFileChooserTitle;
  }

  public String getDialogDescription() {
    return myDialogDescription;
  }
}
