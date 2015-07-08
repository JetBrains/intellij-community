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

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.geom.Ellipse2D;

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

  public static void handleExecutionError(@NotNull ExecutionEnvironment environment, @NotNull ExecutionException e) {
    handleExecutionError(environment.getProject(), environment.getExecutor().getToolWindowId(), environment.getRunProfile().getName(), e);
  }

  public static void handleExecutionError(@NotNull final Project project,
                                          @NotNull final String toolWindowId,
                                          @NotNull String taskName,
                                          @NotNull ExecutionException e) {
    if (e instanceof RunCanceledByUserException) {
      return;
    }

    LOG.debug(e);

    String description = e.getMessage();
    if (description == null) {
      LOG.warn("Execution error without description", e);
      description = "Unknown error";
    }

    HyperlinkListener listener = null;
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
      LOG.error(fullMessage, e);
    }

    if (listener == null && e instanceof HyperlinkListener) {
      listener = (HyperlinkListener)e;
    }

    final HyperlinkListener finalListener = listener;
    final String finalDescription = description;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) {
          return;
        }

        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        if (toolWindowManager.canShowNotification(toolWindowId)) {
          //noinspection SSBasedInspection
          toolWindowManager.notifyByBalloon(toolWindowId, MessageType.ERROR, fullMessage, null, finalListener);
        }
        else {
          Messages.showErrorDialog(project, UIUtil.toHtml(fullMessage), "");
        }
        NotificationListener notificationListener = finalListener == null ? null : new NotificationListener() {
          @Override
          public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            finalListener.hyperlinkUpdate(event);
          }
        };
        ourNotificationGroup.createNotification(title, finalDescription, NotificationType.ERROR, notificationListener).notify(project);
      }
    });
  }

  public static void restartIfActive(@NotNull RunContentDescriptor descriptor) {
    ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler != null
        && processHandler.isStartNotified()
        && !processHandler.isProcessTerminating()
        && !processHandler.isProcessTerminated()) {
      restart(descriptor);
    }
  }

  public static void restart(@NotNull RunContentDescriptor descriptor) {
    restart(descriptor.getComponent());
  }

  public static void restart(@NotNull Content content) {
    restart(content.getComponent());
  }

  private static void restart(@Nullable JComponent component) {
    if (component != null) {
      ExecutionEnvironment environment = LangDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component));
      if (environment != null) {
        restart(environment);
      }
    }
  }

  public static void restart(@NotNull ExecutionEnvironment environment) {
    if (!ExecutorRegistry.getInstance().isStarting(environment)) {
      ExecutionManager.getInstance(environment.getProject()).restartRunProfile(environment);
    }
  }

  public static void runConfiguration(@NotNull RunnerAndConfigurationSettings configuration, @NotNull Executor executor) {
    ExecutionEnvironmentBuilder builder = createEnvironment(executor, configuration);
    if (builder != null) {
      ExecutionManager.getInstance(configuration.getConfiguration().getProject()).restartRunProfile(builder
                                                                                                      .activeTarget()
                                                                                                      .build());
    }
  }

  @Nullable
  public static ExecutionEnvironmentBuilder createEnvironment(@NotNull Executor executor, @NotNull RunnerAndConfigurationSettings settings) {
    try {
      return ExecutionEnvironmentBuilder.create(executor, settings);
    }
    catch (ExecutionException e) {
      handleExecutionError(settings.getConfiguration().getProject(), executor.getToolWindowId(), settings.getConfiguration().getName(), e);
      return null;
    }
  }

  public static Icon getLiveIndicator(@Nullable final Icon base) {
    return new LayeredIcon(base, new Icon() {
      @SuppressWarnings("UseJBColor")
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        int iSize = JBUI.scale(4);
        Graphics2D g2d = (Graphics2D)g.create();
        try {
          GraphicsUtil.setupAAPainting(g2d);
          g2d.setColor(Color.GREEN);
          Ellipse2D.Double shape =
            new Ellipse2D.Double(x + getIconWidth() - JBUI.scale(iSize), y + getIconHeight() - iSize, iSize, iSize);
          g2d.fill(shape);
          g2d.setColor(ColorUtil.withAlpha(Color.BLACK, .40));
          g2d.draw(shape);
        }
        finally {
          g2d.dispose();
        }
      }

      @Override
      public int getIconWidth() {
        return base != null ? base.getIconWidth() : 13;
      }

      @Override
      public int getIconHeight() {
        return base != null ? base.getIconHeight() : 13;
      }
    });
  }
}
