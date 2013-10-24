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
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private static final Pattern ERROR_LOCATION_PATTERN;

  static {
    ERROR_LOCATION_PATTERN = Pattern.compile("error in file: (.*?) at line: (\\d+)");
  }

  @NotNull private final AtomicReference<Notification> myNotification = new AtomicReference<Notification>();

  public void processExternalProjectRefreshError(@NotNull Throwable error,
                                                 @NotNull final Project project,
                                                 @NotNull String externalProjectName,
                                                 @NotNull ProjectSystemId externalSystemId)
  {
    if (project.isDisposed() || !project.isOpen()) {
      return;
    }
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (!(manager instanceof ExternalSystemConfigurableAware)) {
      return;
    }

    String message = ExternalSystemApiUtil.buildErrorMessage(error);
    String title = ExternalSystemBundle.message("notification.project.refresh.fail.description",
                                                externalSystemId.getReadableName(), externalProjectName, message);
    String messageToShow = ExternalSystemBundle.message("notification.action.show.settings", externalSystemId.getReadableName());
    NotificationType notificationType = NotificationType.WARNING;
    final Configurable configurable = ((ExternalSystemConfigurableAware)manager).getConfigurable(project);
    NotificationListener listener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
          return;
        }
        if ("configure".equals(event.getDescription())) {
          ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
        }
        else if (!StringUtil.isEmpty(event.getDescription())) {
          Matcher matcher = ERROR_LOCATION_PATTERN.matcher(event.getDescription());
          if(matcher.find()) {
            String file = matcher.group(1);
            String lineText = matcher.group(2);
            Integer line;
            try {
              line = Integer.valueOf(lineText);
            }
            catch (NumberFormatException e) {
              line = 0;
            }
            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file);
            if(virtualFile != null) {
              new OpenFileDescriptor(project, virtualFile, line - 1, -1).navigate(true);
            }
          }
        }
      }
    };
    
    for (ExternalSystemNotificationExtension extension : ExternalSystemNotificationExtension.EP_NAME.getExtensions()) {
      if (!externalSystemId.equals(extension.getTargetExternalSystemId())) {
        continue;
      }
      ExternalSystemNotificationExtension.CustomizationResult customizationResult = extension.customize(
        project, error, ExternalSystemNotificationExtension.UsageHint.PROJECT_REFRESH
      );
      if (customizationResult == null) {
        continue;
      }
      if (customizationResult.getTitle() != null) {
        title = customizationResult.getTitle();
      }
      if (customizationResult.getMessage() != null) {
        messageToShow = customizationResult.getMessage();
      }
      if (customizationResult.getNotificationType() != null) {
        notificationType = customizationResult.getNotificationType();
      }
      if (customizationResult.getListener() != null) {
        listener = customizationResult.getListener();
      }
    }

    EditorNotifications.getInstance(project).updateAllNotifications();
    showNotification(title, messageToShow, notificationType, project, externalSystemId, listener);
  }

  public void showNotification(@NotNull final String title,
                               @NotNull final String message,
                               @NotNull final NotificationType type,
                               @NotNull final Project project,
                               @NotNull final ProjectSystemId externalSystemId,
                               @Nullable final NotificationListener listener)
  {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
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
    });
    
  }
  
  private void applyNotification(@NotNull final Notification notification, @NotNull final Project project) {
    final Notification oldNotification = myNotification.get();
    if (oldNotification != null && myNotification.compareAndSet(oldNotification, null)) {
      oldNotification.expire();
    }
    if (!myNotification.compareAndSet(null, notification)) {
      notification.expire();
      return;
    }

    if (!project.isDisposed() && project.isOpen()) {
      notification.notify(project);
    }
  }
}
