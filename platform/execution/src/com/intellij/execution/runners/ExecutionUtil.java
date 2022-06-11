// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ExecutionDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.content.Content;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.function.Function;

public final class ExecutionUtil {
  public static final String PROPERTY_DYNAMIC_CLASSPATH = "dynamic.classpath";

  private static final Logger LOG = Logger.getInstance(ExecutionUtil.class);

  private static final NotificationGroup ourSilentNotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("Silent Execution");
  private static final NotificationGroup ourNotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("Execution");

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
    if (e instanceof CantRunException.CustomProcessedCantRunException) {
      return;
    }

    String description = e.getMessage();
    HyperlinkListener listener = null;
    if (isProcessNotCreated(e)) {
      String exePath = ((ProcessNotCreatedException)e).getCommandLine().getExePath();
      if ((SystemInfoRt.isWindows ? exePath.endsWith("java.exe") : exePath.endsWith("java")) &&
          !PropertiesComponent.getInstance(project).isTrueValue(PROPERTY_DYNAMIC_CLASSPATH)) {
        LOG.warn("Java configuration should implement `ConfigurationWithCommandLineShortener` and provide UI to configure shortening method", e);
        description = ExecutionBundle.message("dialog.message.command.line.too.long.notification");
        listener = event -> PropertiesComponent.getInstance(project).setValue(PROPERTY_DYNAMIC_CLASSPATH, "true");
      }
    }

    handleExecutionError(project, toolWindowId, taskName, e, description, listener);
  }

  public static boolean isProcessNotCreated(@NotNull Throwable e) {
    if (e instanceof ProcessNotCreatedException) {
      String description = e.getMessage();
      return (description.contains("87") || description.contains("111") || description.contains("206") || description.contains("error=7,")) &&
             ((ProcessNotCreatedException)e).getCommandLine().getCommandLineString().length() > 1024 * 32;
    }
    return false;
  }

  public static void handleExecutionError(@NotNull Project project,
                                          @NotNull String toolWindowId,
                                          @NotNull String taskName,
                                          @NotNull Throwable e,
                                          @Nullable @DialogMessage String description,
                                          @Nullable HyperlinkListener listener) {
    String title = ExecutionBundle.message("error.running.configuration.message", taskName);
    handleExecutionError(project, toolWindowId, e, title, description, descr -> title + ":<br>" + descr, listener);
  }

  public static void handleExecutionError(@NotNull Project project,
                                          @NotNull String toolWindowId,
                                          @NotNull Throwable e,
                                          @Nls String title,
                                          @Nullable @DialogMessage String description,
                                          @NotNull Function<? super @DialogMessage String, @DialogMessage String> fullMessageSupplier,
                                          @Nullable HyperlinkListener listener) {

    if (StringUtil.isEmptyOrSpaces(description)) {
      LOG.warn("Execution error without description", e);
      description = ExecutionBundle.message("dialog.message.unknown.error");
    }

    String fullMessage = fullMessageSupplier.apply(description);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(fullMessage, e);
    }
    else {
      LOG.info(fullMessage, e);
    }

    if (e instanceof ProcessNotCreatedException) {
      LOG.debug("Attempting to run: " + ((ProcessNotCreatedException)e).getCommandLine().getCommandLineString());
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

      boolean balloonShown = IdeUiService.getInstance().notifyByBalloon(project, toolWindowId, MessageType.ERROR,
                                                                        fullMessage, null, _listener);

      NotificationGroup notificationGroup = balloonShown ? ourSilentNotificationGroup : ourNotificationGroup;
      Notification notification = notificationGroup.createNotification(title, _description, NotificationType.ERROR);
      if (_listener != null) {
        notification.setListener((_notification, event) -> {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            _notification.expire();
            _listener.hyperlinkUpdate(event);
          }
        });
      }
      notification.notify(project);
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
      ExecutionEnvironment environment = ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component));
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
    return new LayeredIcon(base, new IndicatorIcon(base, emptyIconWidth, emptyIconHeight, color));
  }
}
