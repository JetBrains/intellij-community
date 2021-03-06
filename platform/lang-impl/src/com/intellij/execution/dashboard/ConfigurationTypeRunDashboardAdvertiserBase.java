package com.intellij.execution.dashboard;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
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
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class ConfigurationTypeRunDashboardAdvertiserBase implements RunManagerListener, Disposable {
  private static final String DASHBOARD_NOTIFICATION_GROUP_ID = "Run Dashboard";
  private static final String DASHBOARD_MULTIPLE_RUN_CONFIGURATIONS_NOTIFICATION_ID = "run.dashboard.multiple.run.configurations";
  private static final String SHOW_RUN_DASHBOARD_NOTIFICATION = "show.run.dashboard.notification";

  private final Project myProject;
  private Notification myNotification;

  public ConfigurationTypeRunDashboardAdvertiserBase(@NotNull Project project) {
    myProject = project;
  }

  protected abstract @NotNull ConfigurationType getType();

  @Override
  public void dispose() {
    if (myNotification != null) {
      myNotification.expire();
      myNotification = null;
    }
  }

  public final void subscribe() {
    if (myProject.isDefault() || ApplicationManager.getApplication().isUnitTestMode() || myProject.isDisposed()) {
      return;
    }

    if (!isEnabled(myProject)) return;

    MessageBusConnection connection = myProject.getMessageBus().connect();
    Disposer.register(connection, this);
    connection.subscribe(RunManagerListener.TOPIC, this);
    checkRunDashboardAvailability();
  }

  @Override
  public final void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
    if (!isEnabled(myProject)) return;

    if (settings.getType().equals(getType())) {
      ApplicationManager.getApplication().invokeLater(() -> checkRunDashboardAvailability(),
                                                      o -> Disposer.isDisposed(this));
    }
  }

  private void checkRunDashboardAvailability() {
    ConfigurationType type = getType();
    List<RunnerAndConfigurationSettings> settings =
      RunManager.getInstance(myProject).getConfigurationSettingsList(type);

    if (settings.size() <= 1) return;
    if (RunDashboardManager.getInstance(myProject).getTypes().contains(type.getId())) {
      return;
    }

    if (myNotification != null && !myNotification.isExpired()) return;

    String toolWindowName = UIBundle.message("tool.window.name.services");
    myNotification = NotificationGroupManager.getInstance().getNotificationGroup(DASHBOARD_NOTIFICATION_GROUP_ID).createNotification(
      toolWindowName,
      ExecutionBundle.message("run.dashboard.multiple.run.config.notification", type.getDisplayName(), toolWindowName),
      NotificationType.INFORMATION,
      null,
      DASHBOARD_MULTIPLE_RUN_CONFIGURATIONS_NOTIFICATION_ID);
    String typeId = type.getId();
    myNotification.addAction(new NotificationAction(ExecutionBundle.message("run.dashboard.use.services.action", toolWindowName)) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e,
                                  @NotNull Notification notification) {
        notification.hideBalloon();
        showInRunDashboard(typeId);
      }
    });
    myNotification.addAction(new NotificationAction(ExecutionBundle.message("run.dashboard.hide.multiple.run.config.notification.action")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e,
                                  @NotNull Notification notification) {
        PropertiesComponent.getInstance(myProject).setValue(SHOW_RUN_DASHBOARD_NOTIFICATION, false, true);
        notification.expire();
      }
    });
    myNotification.notify(myProject);
  }

  private static boolean isEnabled(Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(SHOW_RUN_DASHBOARD_NOTIFICATION, true);
  }

  private void showInRunDashboard(String typeId) {
    RunDashboardManager dashboardManager = RunDashboardManager.getInstance(myProject);
    Set<String> types = new HashSet<>(dashboardManager.getTypes());
    types.add(typeId);
    dashboardManager.setTypes(types);
  }
}
