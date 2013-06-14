package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.externalSystem.ExternalSystemConfigurableAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is responsible for ide user by external system integration-specific events.
 * <p/>
 * One example use-case is a situation when an error occurs during external project refresh. We need to
 * show corresponding message to the end-user.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/21/12 4:04 PM
 */
public class ExternalSystemIdeNotificationManager {

  @NotNull private final AtomicReference<Notification> myNotification = new AtomicReference<Notification>();

  public void processExternalProjectRefreshError(@NotNull String message,
                                                 @NotNull final Project project,
                                                 @NotNull String externalProjectName,
                                                 @NotNull ProjectSystemId externalSystemId)
  {
    ExternalSystemManager<?,?,?,?,?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (!(manager instanceof ExternalSystemConfigurableAware)) {
      return;
    }
    final Configurable configurable = ((ExternalSystemConfigurableAware)manager).getConfigurable(project);

    EditorNotifications.getInstance(project).updateAllNotifications();
    String title = ExternalSystemBundle.message("notification.project.refresh.fail.description",
                                                externalSystemId.getReadableName(), externalProjectName, message);
    String messageToShow = ExternalSystemBundle.message("notification.action.show.settings", externalSystemId.getReadableName());
    showNotification(title, messageToShow, NotificationType.WARNING, project, externalSystemId, new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
          return;
        }
        ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
      }
    });
  }

  public void showNotification(@NotNull String title,
                               @NotNull String message,
                               @NotNull NotificationType type,
                               @NotNull Project project,
                               @NotNull ProjectSystemId externalSystemId,
                               @Nullable NotificationListener listener)
  {
    NotificationGroup group = ExternalSystemUtil.getToolWindowElement(NotificationGroup.class,
                                                                      project,
                                                                      ExternalSystemDataKeys.NOTIFICATION_GROUP,
                                                                      externalSystemId);
    if (group == null) {
      return;
    }

    Notification notification = group.createNotification(title, message, type, listener);
    applyNotification(notification, project);
  }
  
  private void applyNotification(@NotNull Notification notification, @NotNull Project project) {
    final Notification oldNotification = myNotification.get();
    if (oldNotification != null && myNotification.compareAndSet(oldNotification, null)) {
      oldNotification.expire();
    }
    if (!myNotification.compareAndSet(null, notification)) {
      notification.expire();
      return;
    }

    notification.notify(project);
  }
}
