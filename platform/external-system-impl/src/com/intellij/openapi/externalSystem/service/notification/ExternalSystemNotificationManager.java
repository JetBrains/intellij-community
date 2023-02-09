// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.build.issue.BuildIssue;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.ide.errorTreeView.*;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.issue.BuildIssueException;
import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.view.ExternalProjectsView;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts.NotificationTitle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ExternalSystemNotificationManager} provides creation and managements of user-friendly notifications for external system integration-specific events.
 * <p/>
 * One example use-case is a situation when an error occurs during external project refresh. We need to
 * show corresponding message to the end-user.
 * <p/>
 * Thread-safe.
 *
 * @author Vladislav Soroka
 */
@Service(Service.Level.PROJECT)
public final class ExternalSystemNotificationManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(ExternalSystemNotificationManager.class);

  private static final @NotNull Key<Pair<NotificationSource, ProjectSystemId>> CONTENT_ID_KEY = Key.create("CONTENT_ID");
  private final @NotNull MergingUpdateQueue myUpdateQueue;
  private volatile @Nullable Project myProject;
  private final @NotNull Set<Notification> myNotifications;
  private final @NotNull Map<Key, Notification> myUniqueNotifications;
  private final @NotNull Set<ProjectSystemId> initializedExternalSystem;
  private final @NotNull MessageCounter myMessageCounter;

  public ExternalSystemNotificationManager(final @NotNull Project project) {
    myProject = project;
    myNotifications = ContainerUtil.newConcurrentSet();
    myUniqueNotifications = new ConcurrentHashMap<>();
    initializedExternalSystem = ContainerUtil.newConcurrentSet();
    myMessageCounter = new MessageCounter();
    myUpdateQueue = new MergingUpdateQueue(getClass() + " updates", 500, true, null, this, null, false);
  }

  public static @NotNull ExternalSystemNotificationManager getInstance(@NotNull Project project) {
    return project.getService(ExternalSystemNotificationManager.class);
  }

  /**
   * Create {@link NotificationData} for error happened during the external system invocation which can be shown to the end user.
   *
   * @return {@link NotificationData} or null for not user-friendly errors.
   */
  public @Nullable NotificationData createNotification(@NotNull @NotificationTitle String title,
                                                       @NotNull Throwable error,
                                                       @NotNull ProjectSystemId externalSystemId,
                                                       @NotNull Project project,
                                                       @NotNull DataContext dataContext) {
    if (isInternalError(error, externalSystemId)) {
      return null;
    }

    String message = ExternalSystemApiUtil.buildErrorMessage(error);
    NotificationCategory notificationCategory = NotificationCategory.ERROR;
    String filePath = null;
    Integer line = null;
    Integer column = null;

    Throwable unwrapped = RemoteUtil.unwrap(error);
    if (unwrapped instanceof LocationAwareExternalSystemException locationAwareExternalSystemException) {
      filePath = locationAwareExternalSystemException.getFilePath();
      line = locationAwareExternalSystemException.getLine();
      column = locationAwareExternalSystemException.getColumn();
    }

    NotificationData notificationData =
      new NotificationData(
        title, message, notificationCategory, NotificationSource.PROJECT_SYNC,
        filePath, ObjectUtils.notNull(line, -1), ObjectUtils.notNull(column, -1), false);

    if (unwrapped instanceof BuildIssueException) {
      BuildIssue buildIssue = ((BuildIssueException)unwrapped).getBuildIssue();
      for (BuildIssueQuickFix quickFix : buildIssue.getQuickFixes()) {
        notificationData.setListener(quickFix.getId(), (notification, event) -> {
          quickFix.runQuickFix(project, dataContext);
        });
      }
      notificationData.setNavigatable(buildIssue.getNavigatable(project));
      return notificationData;
    }

    for (ExternalSystemNotificationExtension extension : ExternalSystemNotificationExtension.EP_NAME.getExtensions()) {
      final ProjectSystemId targetExternalSystemId = extension.getTargetExternalSystemId();
      if (!externalSystemId.equals(targetExternalSystemId) && !targetExternalSystemId.equals(ProjectSystemId.IDE)) {
        continue;
      }
      extension.customize(notificationData, project, error);
    }
    return notificationData;
  }

  private static boolean isInternalError(@NotNull Throwable error,
                                         @NotNull ProjectSystemId externalSystemId) {
    if (RemoteUtil.unwrap(error) instanceof BuildIssueException) return false;
    return ExternalSystemNotificationExtension.EP_NAME.getExtensionList().stream()
      .anyMatch(extension -> externalSystemId.equals(extension.getTargetExternalSystemId()) && extension.isInternalError(error));
  }

  public boolean isNotificationActive(@NotNull Key<String> notificationKey) {
    Notification notification = myUniqueNotifications.get(notificationKey);
    return notification != null && !notification.isExpired();
  }

  public void showNotification(final @NotNull ProjectSystemId externalSystemId, final @NotNull NotificationData notificationData) {
    showNotification(externalSystemId, notificationData, null);
  }

  public void showNotification(final @NotNull ProjectSystemId externalSystemId,
                               final @NotNull NotificationData notificationData,
                               @Nullable Key<String> notificationKey) {
    Disposer.register(this, notificationData);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      if (notificationData.getNotificationCategory() == NotificationCategory.INFO) {
        LOG.debug(notificationData.getMessage());
      } else {
        LOG.warn(notificationData.getMessage());
      }
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      @SuppressWarnings("UseOfSystemOutOrSystemErr")
      PrintStream out = notificationData.getNotificationCategory() == NotificationCategory.INFO ? System.out : System.err;
      out.println(notificationData.getMessage());
      return;
    }

    if (notificationKey != null && isNotificationActive(notificationKey)) return;
    myUpdateQueue.queue(new Update(new Object()) {

      @Override
      public void run() {
        if (isDisposedOrNotOpen()) return;
        assert myProject != null;
        Project project = myProject;

        final Application app = ApplicationManager.getApplication();
        Runnable action = () -> {
          if (!initializedExternalSystem.contains(externalSystemId)) {
            app.runWriteAction(() -> {
              if (isDisposedOrNotOpen()) return;
              ExternalSystemUtil.ensureToolWindowContentInitialized(project, externalSystemId);
              initializedExternalSystem.add(externalSystemId);
            });
          }
          if (isDisposedOrNotOpen()) return;
          NotificationGroup group;
          if (notificationData.getBalloonGroup() == null) {
            ExternalProjectsView externalProjectsView =
              ExternalProjectsManagerImpl.getInstance(project).getExternalProjectsView(externalSystemId);
            group = externalProjectsView instanceof ExternalProjectsViewImpl ?
                    ((ExternalProjectsViewImpl)externalProjectsView).getNotificationGroup() : null;
          }
          else {
            group = notificationData.getBalloonGroup();
          }
          if (group == null) return;

          final Notification notification = group
            .createNotification(notificationData.getTitle(), notificationData.getMessage(), notificationData.getNotificationCategory().getNotificationType())
            .setListener(notificationData.getListener());

          if (notificationKey == null) {
            myNotifications.add(notification);
          }
          else {
            Notification oldNotification = myUniqueNotifications.put(notificationKey, notification);
            if (oldNotification != null) {
              oldNotification.expire();
            }
          }

          if (notificationData.isBalloonNotification()) {
            applyNotification(notification);
          }
          else {
            addMessage(notification, externalSystemId, notificationData);
          }
        };
        app.invokeLater(action, ModalityState.defaultModalityState(), project.getDisposed());
      }
    });
  }

  @Deprecated
  public void clearNotifications(final @Nullable String groupName,
                                 final @NotNull NotificationSource notificationSource,
                                 final @NotNull ProjectSystemId externalSystemId) {
    myMessageCounter.remove(groupName, notificationSource, externalSystemId);
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final Pair<NotificationSource, ProjectSystemId> contentIdPair = Pair.create(notificationSource, externalSystemId);
    myUpdateQueue.queue(new Update(new Object()) {
      @Override
      public void run() {
        if (isDisposedOrNotOpen()) return;
        assert myProject != null;
        Project project = myProject;

        for (Iterator<Notification> iterator = myNotifications.iterator(); iterator.hasNext(); ) {
          Notification notification = iterator.next();
          if (groupName == null || groupName.equals(notification.getGroupId())) {
            notification.expire();
            iterator.remove();
          }
        }

        List<Key> toRemove = new SmartList<>();
        myUniqueNotifications.forEach((key, notification) -> {
          if (groupName == null || groupName.equals(notification.getGroupId())) {
            notification.expire();
            toRemove.add(key);
          }
        });
        toRemove.forEach(myUniqueNotifications::remove);

        final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
        if (toolWindow == null) return;

        final MessageView messageView = project.getService(MessageView.class);
        UIUtil.invokeLaterIfNeeded(() -> {
          if (project.isDisposed()) return;
          for (Content content: messageView.getContentManager().getContents()) {
            if (!content.isPinned() && contentIdPair.equals(content.getUserData(CONTENT_ID_KEY))) {
              if (groupName == null) {
                messageView.getContentManager().removeContent(content, true);
              }
              else {
                assert content.getComponent() instanceof NewEditableErrorTreeViewPanel;
                NewEditableErrorTreeViewPanel errorTreeView = (NewEditableErrorTreeViewPanel)content.getComponent();
                ErrorViewStructure errorViewStructure = errorTreeView.getErrorViewStructure();
                errorViewStructure.removeGroup(groupName);
              }
            }
          }
        });
      }
    });
  }

  private void addMessage(final @NotNull Notification notification,
                          final @NotNull ProjectSystemId externalSystemId,
                          final @NotNull NotificationData notificationData) {
    final VirtualFile virtualFile =
      notificationData.getFilePath() != null ? ExternalSystemUtil.findLocalFileByPath(notificationData.getFilePath()) : null;
    final String groupName = virtualFile != null ? virtualFile.getPresentableUrl() : notificationData.getTitle();

    myMessageCounter
      .increment(groupName, notificationData.getNotificationSource(), notificationData.getNotificationCategory(), externalSystemId);

    int line = notificationData.getLine() - 1;
    int column = notificationData.getColumn() - 1;
    if (virtualFile == null) line = column = -1;
    final int guiLine = line < 0 ? -1 : line + 1;
    final int guiColumn = column < 0 ? 0 : column + 1;

    if (isDisposedOrNotOpen()) return;
    assert myProject != null;
    Project project = myProject;
    final Navigatable navigatable = notificationData.getNavigatable() != null
                                    ? notificationData.getNavigatable()
                                    : virtualFile != null
                                      ? new OpenFileDescriptor(project, virtualFile, line, column)
                                      : NonNavigatable.INSTANCE;

    final ErrorTreeElementKind kind =
      ErrorTreeElementKind.convertMessageFromCompilerErrorType(notificationData.getNotificationCategory().getMessageCategory());
    final String[] message = notificationData.getMessage().split("\n");
    final String exportPrefix = NewErrorTreeViewPanel.createExportPrefix(guiLine);
    final String rendererPrefix = NewErrorTreeViewPanel.createRendererPrefix(guiLine, guiColumn);

    UIUtil.invokeLaterIfNeeded(() -> {
      boolean activate =
        notificationData.getNotificationCategory() == NotificationCategory.ERROR ||
        notificationData.getNotificationCategory() == NotificationCategory.WARNING;
      final NewErrorTreeViewPanel errorTreeView =
        prepareMessagesView(externalSystemId, notificationData.getNotificationSource(), activate);
      final GroupingElement groupingElement = errorTreeView.getErrorViewStructure().getGroupingElement(groupName, null, virtualFile);
      final NavigatableMessageElement navigatableMessageElement;
      // Note: Given that screen readers don't currently support hyperlinks and
      // that having a cell editor for a panel in a tree view node makes
      // the user-interaction confusing for keyboard only users,
      // don't create a editable element if screen reader is active.
      if (notificationData.hasLinks() && !ScreenReader.isActive()) {
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

      errorTreeView.getErrorViewStructure().addNavigatableMessage(groupName, navigatableMessageElement);
      errorTreeView.updateTree();
    });
  }

  private void applyNotification(final @NotNull Notification notification) {
    if (!isDisposedOrNotOpen()) {
      notification.notify(myProject);
    }
  }

  @Deprecated
  public @NotNull NewErrorTreeViewPanel prepareMessagesView(final @NotNull ProjectSystemId externalSystemId,
                                                            final @NotNull NotificationSource notificationSource,
                                                            boolean activateView) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final NewErrorTreeViewPanel errorTreeView;
    final String contentDisplayName = getContentDisplayName(notificationSource, externalSystemId);
    final Pair<NotificationSource, ProjectSystemId> contentIdPair = Pair.create(notificationSource, externalSystemId);
    Content targetContent = findContent(contentIdPair, contentDisplayName);

    assert myProject != null;
    final MessageView messageView = myProject.getService(MessageView.class);
    if (targetContent == null || !contentIdPair.equals(targetContent.getUserData(CONTENT_ID_KEY))) {
      errorTreeView = new NewEditableErrorTreeViewPanel(myProject, null, true, true, null);
      targetContent = ContentFactory.getInstance().createContent(errorTreeView, contentDisplayName, true);
      targetContent.putUserData(CONTENT_ID_KEY, contentIdPair);

      messageView.getContentManager().addContent(targetContent);
      Disposer.register(targetContent, errorTreeView);
    }
    else {
      assert targetContent.getComponent() instanceof NewEditableErrorTreeViewPanel;
      errorTreeView = (NewEditableErrorTreeViewPanel)targetContent.getComponent();
    }

    messageView.getContentManager().setSelectedContent(targetContent);
    final ToolWindow tw = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
    if (activateView && tw != null && !tw.isActive()) {
      tw.activate(null, false);
    }
    return errorTreeView;
  }

  private @Nullable Content findContent(@NotNull Pair<NotificationSource, ProjectSystemId> contentIdPair, @NotNull String contentDisplayName) {
    Content targetContent = null;
    assert myProject != null;
    final MessageView messageView = myProject.getService(MessageView.class);
    for (Content content: messageView.getContentManager().getContents()) {
      if (contentIdPair.equals(content.getUserData(CONTENT_ID_KEY))
          && StringUtil.equals(content.getDisplayName(), contentDisplayName) && !content.isPinned()) {
        targetContent = content;
      }
    }
    return targetContent;
  }

  @Deprecated
  public static @NotNull @Nls String getContentDisplayName(
    final @NotNull NotificationSource notificationSource,
    final @NotNull ProjectSystemId externalSystemId
  ) {
    return ExternalSystemBundle.message(switch (notificationSource) {
      case PROJECT_SYNC -> "notification.messages.project.sync.tab.name";
      case TASK_EXECUTION -> "notification.messages.task.execution.tab.name";
    }, externalSystemId.getReadableName());
  }

  @Override
  public void dispose() {
    myProject = null;
    myNotifications.clear();
    myUniqueNotifications.clear();
    initializedExternalSystem.clear();
  }

  private boolean isDisposedOrNotOpen() {
    return myProject == null || myProject.isDisposed() || !myProject.isOpen();
  }
}
