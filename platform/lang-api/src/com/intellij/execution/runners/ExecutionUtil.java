/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.runners;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

public class ExecutionUtil {
  public static final String NOTIFICATION_GROUP_ID = "Execution";

  private static final Logger LOG = Logger.getInstance("com.intellij.execution.runners.ExecutionUtil");

  private ExecutionUtil() {
  }

  public static void handleExecutionError(final Project project, @NotNull String taskName, final ExecutionException e) {
    if (e instanceof RunCanceledByUserException) return;

    String title = ExecutionBundle.message("error.running.configuration.with.error.error.message", taskName);
    String message = e.getMessage();
    NotificationDisplayType type = NotificationDisplayType.BALLOON;
    NotificationListener listener = null;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(message + ":" + message);
    }
    else {
      if (message.contains("87") && e instanceof ProcessNotCreatedException) {
        final String commandLineString = ((ProcessNotCreatedException)e).getCommandLine().getCommandLineString();
        if (commandLineString.length() > 1024 * 32) {
          type = NotificationDisplayType.STICKY_BALLOON;
          message += "\n" +
                     "Command line is too long. In order to reduce its length classpath file can be used.<br>" +
                     "Would you like to enable classpath file mode for all run configurations of your project?<br>" +
                     "<a href=\"\">Enable</a>";

          listener = new NotificationListener() {
            public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
              PropertiesComponent.getInstance(project).setValue("dynamic.classpath", "true");
              notification.expire();
            }
          };
        }
      }
    }
    Notification n = new Notification(NOTIFICATION_GROUP_ID, title, message, NotificationType.ERROR, listener);
    Notifications.Bus.notify(n, type, project);
  }

  public static void handleExecutionError(final Project project, @NotNull final RunProfile runProfile, final ExecutionException e) {
    handleExecutionError(project, runProfile.getName(), e);
  }
}
