// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.UIBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ConfigurationTypeRunDashboardAdvertiser implements RunManagerListener, Disposable {
  public static final String DASHBOARD_NOTIFICATION_GROUP_ID = "Services Tool Window";
  private static final String DASHBOARD_MULTIPLE_RUN_CONFIGURATIONS_NOTIFICATION_ID = "run.dashboard.multiple.run.configurations";
  private static final String SHOW_RUN_DASHBOARD_NOTIFICATION = "show.run.dashboard.notification";

  private final Project myProject;
  private final String myRunConfigurationTypeId;
  private Notification myNotification;
  private volatile boolean myDisposed;

  public ConfigurationTypeRunDashboardAdvertiser(@NotNull Project project, String runConfigurationTypeId) {
    myProject = project;
    myRunConfigurationTypeId = runConfigurationTypeId;
  }

  @Override
  public void dispose() {
    if (myNotification != null) {
      myNotification.expire();
      myNotification = null;
    }
    myDisposed = true;
  }

  public void subscribe(Supplier<? extends Disposable> parentDisposableSupplier) {
    if (myProject.isDefault() || ApplicationManager.getApplication().isUnitTestMode() || myProject.isDisposed()) {
      return;
    }

    Disposable parentDisposable = parentDisposableSupplier.get();
    MessageBusConnection connection = myProject.getMessageBus().connect(parentDisposable);
    Disposer.register(parentDisposable, this);
    connection.subscribe(RunManagerListener.TOPIC, this);
    if (!migrateNotificationProperty(myProject) && isEnabled(myProject)) {
      checkRunDashboardAvailability();
    }
  }

  @Override
  public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
    if (myRunConfigurationTypeId.equals(settings.getType().getId()) && isEnabled(myProject)) {
      ApplicationManager.getApplication().invokeLater(this::checkRunDashboardAvailability, o -> myDisposed);
    }
  }

  private void checkRunDashboardAvailability() {
    List<RunnerAndConfigurationSettings> settings =
      ContainerUtil.filter(RunManager.getInstance(myProject).getAllSettings(),
                           c -> myRunConfigurationTypeId.equals(c.getType().getId()));

    if (settings.size() <= 1) return;
    if (RunDashboardManager.getInstance(myProject).getTypes().contains(myRunConfigurationTypeId)) {
      return;
    }

    if (myNotification != null && !myNotification.isExpired()) return;

    myNotification = createNotification(myProject, myRunConfigurationTypeId, getRunConfigurationDisplayName());
    myNotification.notify(myProject);
  }

  private String getRunConfigurationDisplayName() {
    ConfigurationType configurationType = ContainerUtil.find(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions(),
                                                             c -> myRunConfigurationTypeId.equals(c.getId()));
    assert configurationType != null;

    return configurationType.getDisplayName();
  }

  private static Notification createNotification(Project project, String typeId, String typeDisplayName) {
    String toolWindowName = UIBundle.message("tool.window.name.services");
    return NotificationGroupManager.getInstance().getNotificationGroup(DASHBOARD_NOTIFICATION_GROUP_ID)
      .createNotification(ExecutionBundle.message("run.dashboard.multiple.run.config.notification", typeDisplayName, toolWindowName),
                          NotificationType.INFORMATION)
      .setDisplayId(DASHBOARD_MULTIPLE_RUN_CONFIGURATIONS_NOTIFICATION_ID)
      .setSuggestionType(true)
      .setIcon(AllIcons.Nodes.Services)
      .addAction(new NotificationAction(ExecutionBundle.message("run.dashboard.use.services.action", toolWindowName)) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          notification.hideBalloon();
          showInRunDashboard(project, typeId);
        }
      })
      .addAction(new NotificationAction(IdeBundle.message("notifications.toolwindow.dont.show.again")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          notification.setDoNotAskFor(null);
          notification.expire();
        }
      });
  }

  private static boolean isEnabled(Project project) {
    Notification notification = createNotification(project, "", "");
    return notification.canShowFor(project);
  }

  private static void showInRunDashboard(Project project, String typeId) {
    RunDashboardManager dashboardManager = RunDashboardManager.getInstance(project);
    Set<String> types = new HashSet<>(dashboardManager.getTypes());
    types.add(typeId);
    dashboardManager.setTypes(types);
  }

  private static boolean migrateNotificationProperty(Project project) {
    boolean isEnabled = PropertiesComponent.getInstance(project).getBoolean(SHOW_RUN_DASHBOARD_NOTIFICATION, true);
    if (isEnabled) return false;

    PropertiesComponent.getInstance(project).setValue(SHOW_RUN_DASHBOARD_NOTIFICATION, true, true);
    Notification notification = createNotification(project, "", "");
    notification.setDoNotAskFor(project);
    return true;
  }
}
