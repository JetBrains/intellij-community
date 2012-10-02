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
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

public class ExecutionUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.execution.runners.ExecutionUtil");

  private static final NotificationGroup ourNotificationGroup = NotificationGroup.logOnlyGroup("Execution");

  private ExecutionUtil() {
  }

  public static void handleExecutionError(@NotNull Project project,
                                          @NotNull String toolWindowId,
                                          @NotNull RunProfile runProfile,
                                          @NotNull ExecutionException e) {
    handleExecutionError(project, toolWindowId, runProfile.getName(), e);
  }

  public static void handleExecutionError(@NotNull final Project project,
                                          @NotNull final String toolWindowId,
                                          @NotNull String taskName,
                                          @NotNull ExecutionException e) {
    if (e instanceof RunCanceledByUserException) return;

    LOG.debug(e);
    
    String description = e.getMessage();
    HyperlinkListener listener = null;
    
    if (description == null) {
      LOG.warn("Execution error without description", e);
      description = "Unknown error";
    }
    
    if ((description.contains("87") || description.contains("111") || description.contains("206")) &&
        e instanceof ProcessNotCreatedException &&
        !PropertiesComponent.getInstance(project).isTrueValue("dynamic.classpath")) {
      final String commandLineString = ((ProcessNotCreatedException)e).getCommandLine().getCommandLineString();
      if (commandLineString.length() > 1024 * 32) {
        description = "Command line is too long. In order to reduce its length classpath file can be used.<br>" +
                      "Would you like to enable classpath file mode for all run configurations of your project?<br>" +
                      "<a href=\"\">Enable</a>";

        listener = new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent event) {
            PropertiesComponent.getInstance(project).setValue("dynamic.classpath", "true");
          }
        };
      }
    }
    final String title = ExecutionBundle.message("error.running.configuration.message", taskName);
    final String fullMessage = title + ":<br>" + description;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(fullMessage);
    }

    if (listener == null && e instanceof HyperlinkListener) {
      listener = (HyperlinkListener)e;
    }

    final HyperlinkListener finalListener = listener;
    final String finalDescription = description;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        ToolWindowManager.getInstance(project).notifyByBalloon(toolWindowId, MessageType.ERROR, fullMessage, null, finalListener);
        NotificationListener notificationListener = ObjectUtils.tryCast(finalListener, NotificationListener.class);
        ourNotificationGroup.createNotification(title, finalDescription, NotificationType.ERROR, notificationListener).notify(project);
      }
    });
  }
}
