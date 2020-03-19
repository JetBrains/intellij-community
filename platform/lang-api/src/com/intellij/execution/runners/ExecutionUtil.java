// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.geom.Ellipse2D;

public final class ExecutionUtil {
  private static final Logger LOG = Logger.getInstance(ExecutionUtil.class);

  private static final NotificationGroup ourNotificationGroup = NotificationGroup.logOnlyGroup("Execution");

  private ExecutionUtil() { }

  public static void handleExecutionError(@NotNull Project project,
                                          @NotNull String toolWindowId,
                                          @NotNull RunProfile runProfile,
                                          @NotNull ExecutionException e) {
    handleExecutionError(project, toolWindowId, runProfile.getName(), e);
  }

  public static void handleExecutionError(@NotNull ExecutionEnvironment environment, @NotNull ExecutionException e) {
    handleExecutionError(environment.getProject(),
                         RunContentManager.getInstance(environment.getProject()).getToolWindowIdByEnvironment(environment),
                         environment.getRunProfile().getName(), e);
  }

  public static void handleExecutionError(@NotNull Project project,
                                          @NotNull String toolWindowId,
                                          @NotNull String taskName,
                                          @NotNull Throwable e) {
    if (e instanceof RunCanceledByUserException) {
      return;
    }

    LOG.debug(e);

    String description = e.getMessage();
    HyperlinkListener listener = null;
    if (isProcessNotCreated(e) && !PropertiesComponent.getInstance(project).isTrueValue("dynamic.classpath")) {
      description = "Command line is too long. In order to reduce its length classpath file can be used.<br>" +
                    "Would you like to enable classpath file mode for all run configurations of your project?<br>" +
                    "<a href=\"\">Enable</a>";
      listener = event -> PropertiesComponent.getInstance(project).setValue("dynamic.classpath", "true");
    }

    handleExecutionError(project, toolWindowId, taskName, e, description, listener);
  }

  public static boolean isProcessNotCreated(@NotNull Throwable e) {
    if (e instanceof ProcessNotCreatedException) {
      String description = e.getMessage();
      return (description.contains("87") || description.contains("111") || description.contains("206")) &&
             ((ProcessNotCreatedException)e).getCommandLine().getCommandLineString().length() > 1024 * 32;
    }
    return false;
  }

  public static void handleExecutionError(@NotNull Project project,
                                          @NotNull String toolWindowId,
                                          @NotNull String taskName,
                                          @NotNull Throwable e,
                                          @Nullable String description,
                                          @Nullable HyperlinkListener listener) {
    String title = ExecutionBundle.message("error.running.configuration.message", taskName);

    if (StringUtil.isEmptyOrSpaces(description)) {
      LOG.warn("Execution error without description", e);
      description = "Unknown error";
    }

    String fullMessage = title + ":<br>" + description;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(fullMessage, e);
    } else {
      LOG.info(fullMessage, e);
    }

    if (listener == null) {
      listener = ExceptionUtil.findCause(e, HyperlinkListener.class);
    }

    HyperlinkListener _listener = listener;
    String _description = description;
    UIUtil.invokeLaterIfNeeded(() -> {
      if (project.isDisposed()) {
        return;
      }

      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
      if (toolWindowManager.canShowNotification(toolWindowId)) {
        //noinspection SSBasedInspection
        toolWindowManager.notifyByBalloon(toolWindowId, MessageType.ERROR, fullMessage, null, _listener);
      }
      else {
        Messages.showErrorDialog(project, UIUtil.toHtml(fullMessage), "");
      }

      NotificationListener notificationListener = _listener == null ? null : (notification, event) -> {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          notification.expire();
          _listener.hyperlinkUpdate(event);
        }
      };
      ourNotificationGroup.createNotification(title, _description, NotificationType.ERROR, notificationListener).notify(project);
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
    ExecutionManager executionManager = ExecutionManager.getInstance(environment.getProject());
    if (!executionManager.isStarting(environment)) {
      executionManager.restartRunProfile(environment);
    }
  }

  public static void runConfiguration(@NotNull RunnerAndConfigurationSettings configuration, @NotNull Executor executor) {
    doRunConfiguration(configuration, executor, null, null, null);
  }

  public static void runConfiguration(@NotNull RunnerAndConfigurationSettings configuration, @NotNull Executor executor, @NotNull ExecutionTarget target) {
    doRunConfiguration(configuration, executor, target, null, null);
  }

  /**
   * @param executionId Id that will be set for {@link ExecutionEnvironment} that is created to run configuration.
   */
  public static void runConfiguration(@NotNull RunnerAndConfigurationSettings configuration,
                                      @NotNull Executor executor,
                                      @NotNull ExecutionTarget target,
                                      long executionId) {
    doRunConfiguration(configuration, executor, target, executionId, null);
  }

  public static void runConfiguration(@NotNull RunnerAndConfigurationSettings configuration, @NotNull Executor executor, long executionId) {
    doRunConfiguration(configuration, executor, null, executionId, null);
  }

  public static void doRunConfiguration(@NotNull RunnerAndConfigurationSettings configuration,
                                        @NotNull Executor executor,
                                        @Nullable ExecutionTarget targetOrNullForDefault,
                                        @Nullable Long executionId,
                                        @Nullable DataContext dataContext) {
    ExecutionEnvironmentBuilder builder = createEnvironment(executor, configuration);
    if (builder == null) {
      return;
    }

    if (targetOrNullForDefault != null) {
      builder.target(targetOrNullForDefault);
    }
    else {
      builder.activeTarget();
    }
    if (executionId != null) {
      builder.executionId(executionId);
    }
    if (dataContext != null) {
      builder.dataContext(dataContext);
    }
    ExecutionManager.getInstance(configuration.getConfiguration().getProject()).restartRunProfile(builder.build());
  }

  @Nullable
  public static ExecutionEnvironmentBuilder createEnvironment(@NotNull Executor executor, @NotNull RunnerAndConfigurationSettings settings) {
    try {
      return ExecutionEnvironmentBuilder.create(executor, settings);
    }
    catch (ExecutionException e) {
      RunConfiguration configuration = settings.getConfiguration();
      Project project = configuration.getProject();
      RunContentManager manager = RunContentManager.getInstance(project);
      String toolWindowId = manager.getContentDescriptorToolWindowId(configuration);
      if (toolWindowId == null) {
        toolWindowId = executor.getToolWindowId();
      }
      handleExecutionError(project, toolWindowId, configuration.getName(), e);
      return null;
    }
  }

  @NotNull
  public static Icon getLiveIndicator(@Nullable final Icon base) {
    return getLiveIndicator(base, 13, 13);
  }

  @SuppressWarnings("UseJBColor")
  @NotNull
  public static Icon getLiveIndicator(@Nullable final Icon base, int emptyIconWidth, int emptyIconHeight) {
    return getIndicator(base, emptyIconWidth, emptyIconHeight, Color.GREEN);
  }

  @NotNull
  public static Icon getIndicator(@Nullable final Icon base, int emptyIconWidth, int emptyIconHeight, Color color) {
    return new LayeredIcon(base, new Icon() {
      @SuppressWarnings("UseJBColor")
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        int iSize = JBUIScale.scale(4);
        Graphics2D g2d = (Graphics2D)g.create();
        try {
          GraphicsUtil.setupAAPainting(g2d);
          g2d.setColor(color);
          Ellipse2D.Double shape =
            new Ellipse2D.Double(x + getIconWidth() - iSize, y + getIconHeight() - iSize, iSize, iSize);
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
        return base != null ? base.getIconWidth() : emptyIconWidth;
      }

      @Override
      public int getIconHeight() {
        return base != null ? base.getIconHeight() : emptyIconHeight;
      }
    });
  }
}