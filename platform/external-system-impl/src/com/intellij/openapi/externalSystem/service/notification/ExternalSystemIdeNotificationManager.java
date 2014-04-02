package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.ide.errorTreeView.*;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.ExternalSystemConfigurableAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is responsible for ide user by external system integration-specific events.
 * <p/>
 * One example use-case is a situation when an error occurs during external project refresh. We need to
 * show corresponding message to the end-user.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov, Vladislav Soroka
 * @since 3/21/12 4:04 PM
 */
public class ExternalSystemIdeNotificationManager {
  @NotNull private static final Key<Pair<NotificationSource, ProjectSystemId>> CONTENT_ID_KEY = Key.create("CONTENT_ID");
  @NotNull private final AtomicReference<Notification> myNotification = new AtomicReference<Notification>();

  public void processExternalProjectRefreshError(@NotNull Throwable error,
                                                 @NotNull final Project project,
                                                 @NotNull String externalProjectName,
                                                 @NotNull ProjectSystemId externalSystemId) {
    if (project.isDisposed() || !project.isOpen()) {
      return;
    }
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    if (!(manager instanceof ExternalSystemConfigurableAware)) {
      return;
    }

    String title =
      ExternalSystemBundle.message("notification.project.refresh.fail.title", externalSystemId.getReadableName(), externalProjectName);
    String message = ExternalSystemApiUtil.buildErrorMessage(error);
    NotificationType notificationType = NotificationType.ERROR;
    String filePath = null;
    Integer line = null;
    Integer column = null;

    //noinspection ThrowableResultOfMethodCallIgnored
    Throwable unwrapped = RemoteUtil.unwrap(error);
    if (unwrapped instanceof LocationAwareExternalSystemException) {
      LocationAwareExternalSystemException locationAwareExternalSystemException = (LocationAwareExternalSystemException)unwrapped;
      filePath = locationAwareExternalSystemException.getFilePath();
      line = locationAwareExternalSystemException.getLine();
      column = locationAwareExternalSystemException.getColumn();
    }

    NotificationData notificationData =
      new NotificationData(
        title, message, notificationType, NotificationSource.PROJECT_SYNC,
        filePath, ObjectUtils.notNull(line, -1), ObjectUtils.notNull(column, -1), false);

    for (ExternalSystemNotificationExtension extension : ExternalSystemNotificationExtension.EP_NAME.getExtensions()) {
      if (!externalSystemId.equals(extension.getTargetExternalSystemId())) {
        continue;
      }
      extension.customize(notificationData, project, error);
    }

    EditorNotifications.getInstance(project).updateAllNotifications();
    showNotification(project, externalSystemId, notificationData);
  }

  public void showNotification(@NotNull final Project project,
                               @NotNull final ProjectSystemId externalSystemId,
                               @NotNull final NotificationData notificationData) {

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) return;

        final NotificationGroup group = ExternalSystemUtil.getToolWindowElement(
          NotificationGroup.class, project, ExternalSystemDataKeys.NOTIFICATION_GROUP, externalSystemId);
        if (group == null) return;

        final Notification notification = group.createNotification(
          notificationData.getTitle(), notificationData.getMessage(),
          notificationData.getNotificationType(), notificationData.getListener());

        if (notificationData.isBalloonNotification()) {
          applyNotification(notification, project);
        }
        else {
          addMessage(notification, project, externalSystemId, notificationData);
        }
      }
    });
  }

  public void clearNotificationMessages(@NotNull final Project project,
                                        @NotNull final NotificationSource notificationSource,
                                        @NotNull final ProjectSystemId externalSystemId) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
        if (toolWindow == null) return;

        final Pair<NotificationSource, ProjectSystemId> contentIdPair = Pair.create(notificationSource, externalSystemId);
        final MessageView messageView = ServiceManager.getService(project, MessageView.class);
        for (Content content : messageView.getContentManager().getContents()) {
          if (!content.isPinned() && contentIdPair.equals(content.getUserData(CONTENT_ID_KEY))) {
            messageView.getContentManager().removeContent(content, true);
          }
        }
      }
    });
  }


  private static void addMessage(@NotNull final Notification notification,
                                 @NotNull final Project project,
                                 @NotNull final ProjectSystemId externalSystemId,
                                 @NotNull final NotificationData notificationData) {
    notification.expire();

    final NewErrorTreeViewPanel[] errorTreeView = {null};
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(project, new Runnable() {
      @Override
      public void run() {
        errorTreeView[0] = prepareMessagesView(project, externalSystemId, notificationData);
      }
    }, "Open message view", null);

    final ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
    if (tw != null) {
      tw.activate(null, false);
    }

    final VirtualFile virtualFile = notificationData.getFilePath() != null
                                    ?
                                    LocalFileSystem.getInstance().refreshAndFindFileByPath(notificationData.getFilePath())
                                    : null;

    final String groupName = virtualFile != null ? virtualFile.getPresentableUrl() : notificationData.getTitle();

    int line = notificationData.getLine() - 1;
    int column = notificationData.getColumn() - 1;

    if (virtualFile == null) line = column = -1;

    final int guiLine = line < 0 ? -1 : line + 1;
    final int guiColumn = column < 0 ? 0 : column + 1;

    final Navigatable navigatable = notificationData.getNavigatable() != null
                                    ? notificationData.getNavigatable()
                                    : virtualFile != null ? new OpenFileDescriptor(project, virtualFile, line, column) : null;

    final ErrorTreeElementKind kind = getErrorTreeElementKind(notificationData.getNotificationType());
    final String[] message = notificationData.getMessage().split("\n");
    final GroupingElement groupingElement = errorTreeView[0].getErrorViewStructure().getGroupingElement(groupName, null, virtualFile);
    final String exportPrefix = NewErrorTreeViewPanel.createExportPrefix(guiLine);
    final String rendererPrefix = NewErrorTreeViewPanel.createRendererPrefix(guiLine, guiColumn);

    final NavigatableMessageElement navigatableMessageElement;
    if (notificationData.hasLinks()) {
      navigatableMessageElement = new EditableNotificationMessageElement(
        notification,
        kind,
        groupingElement,
        message,
        navigatable,
        exportPrefix,
        rendererPrefix);
    }
    else {
      navigatableMessageElement = new NotificationMessageElement(
        kind,
        groupingElement,
        message,
        navigatable,
        exportPrefix,
        rendererPrefix);
    }

    errorTreeView[0].getErrorViewStructure().addNavigatableMessage(groupName, navigatableMessageElement);
    errorTreeView[0].updateTree();
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

  @NotNull
  private static ErrorTreeElementKind getErrorTreeElementKind(@NotNull final NotificationType notificationType) {
    ErrorTreeElementKind errorTreeElementKind = ErrorTreeElementKind.GENERIC;
    switch (notificationType) {
      case INFORMATION:
        errorTreeElementKind = ErrorTreeElementKind.INFO;
        break;
      case WARNING:
        errorTreeElementKind = ErrorTreeElementKind.WARNING;
        break;
      case ERROR:
        errorTreeElementKind = ErrorTreeElementKind.ERROR;
        break;
    }
    return errorTreeElementKind;
  }

  @NotNull
  private static NewErrorTreeViewPanel prepareMessagesView(@NotNull final Project project,
                                                           @NotNull final ProjectSystemId externalSystemId,
                                                           @NotNull final NotificationData notificationData) {
    final NotificationSource notificationSource = notificationData.getNotificationSource();
    final String contentDisplayName = getContentDisplayName(notificationSource, externalSystemId);
    final Pair<NotificationSource, ProjectSystemId> contentIdPair = Pair.create(notificationSource, externalSystemId);

    Content projectSyncContent = null;
    final MessageView messageView = ServiceManager.getService(project, MessageView.class);
    for (Content content : messageView.getContentManager().getContents()) {
      if (contentIdPair.equals(content.getUserData(CONTENT_ID_KEY))
          && StringUtil.equals(content.getDisplayName(), contentDisplayName) && !content.isPinned()) {
        projectSyncContent = content;
      }
    }
    final NewEditableErrorTreeViewPanel errorTreeView;
    if (projectSyncContent == null || !contentIdPair.equals(projectSyncContent.getUserData(CONTENT_ID_KEY))) {
      errorTreeView = new NewEditableErrorTreeViewPanel(project, null, true, true, null);

      projectSyncContent = ContentFactory.SERVICE.getInstance().createContent(errorTreeView, contentDisplayName, true);
      projectSyncContent.putUserData(CONTENT_ID_KEY, contentIdPair);

      messageView.getContentManager().addContent(projectSyncContent);
      Disposer.register(projectSyncContent, errorTreeView);
    }
    else {
      assert projectSyncContent.getComponent() instanceof NewEditableErrorTreeViewPanel;
      errorTreeView = (NewEditableErrorTreeViewPanel)projectSyncContent.getComponent();
    }

    messageView.getContentManager().setSelectedContent(projectSyncContent);
    return errorTreeView;
  }

  @NotNull
  private static String getContentDisplayName(@NotNull final NotificationSource notificationSource,
                                              @NotNull final ProjectSystemId externalSystemId) {
    final String contentDisplayName;
    switch (notificationSource) {
      case PROJECT_SYNC:
        contentDisplayName =
          ExternalSystemBundle.message("notification.messages.project.sync.tab.name", externalSystemId.getReadableName());
        break;
      default:
        throw new AssertionError("unsupported notification source found: " + notificationSource);
    }
    return contentDisplayName;
  }
}