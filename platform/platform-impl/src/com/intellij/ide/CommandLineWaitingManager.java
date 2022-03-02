// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.ide.lightEdit.LightEditorInfo;
import com.intellij.ide.lightEdit.LightEditorListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Service(Service.Level.APP)
public final class CommandLineWaitingManager {
  private static final Logger LOG = Logger.getInstance(CommandLineWaitingManager.class);
  private static final String DO_NOT_SHOW_KEY = "command.line.waiting.do.not.show";

  private final Map<Object, CompletableFuture<CliResult>> myFileOrProjectToCallback = Collections.synchronizedMap(new HashMap<>());

  private final Set<Object> myDismissedObjects = Collections.synchronizedSet(new HashSet<>());

  private CommandLineWaitingManager() {
    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect();
    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        freeObject(file);
      }
    });
    LightEditService.getInstance().getEditorManager().addListener(new LightEditorListener() {
      @Override
      public void afterClose(@NotNull LightEditorInfo editorInfo) {
        freeObject(editorInfo.getFile());
        Path path = editorInfo.getPreferredSavePath();
        if (path != null) {
          freeObject(path);
        }
      }
    });
    busConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        freeObject(project);
      }
    });
  }

  public @NotNull Future<CliResult> addHookForPath(@NotNull Path path) {
    return addHookAndNotify(path, IdeBundle.message("activation.file.is.waiting.notification", path.toString()));
  }

  public static @NotNull CommandLineWaitingManager getInstance() {
    return ApplicationManager.getApplication().getService(CommandLineWaitingManager.class);
  }

  public @NotNull Future<CliResult> addHookForFile(@NotNull VirtualFile file) {
    return addHookAndNotify(file, IdeBundle.message("activation.file.is.waiting.notification", file.getPath()));
  }

  public @NotNull Future<CliResult> addHookForProject(@NotNull Project project) {
    return addHookAndNotify(project, IdeBundle.message("activation.project.is.waiting.notification", project.getName()));
  }

  private @NotNull Future<CliResult> addHookAndNotify(@NotNull Object fileOrProject,
                                                      @NotNull @NlsContexts.NotificationContent String notificationText) {
    LOG.info(notificationText);

    final CompletableFuture<CliResult> result = new CompletableFuture<>();
    myFileOrProjectToCallback.put(fileOrProject, result);
    Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                                              IdeBundle.message("notification.title.activated.from.command.line"),
                                              notificationText,
                                              NotificationType.WARNING).setImportant(true));

    EditorNotifications.updateAll();

    return result;
  }

  public boolean hasHookFor(@NotNull VirtualFile file) {
    Path path = LightEditUtil.getPreferredSavePathForNonExistentFile(file);
    if (path != null && myFileOrProjectToCallback.containsKey(path)) {
      return true;
    }
    return myFileOrProjectToCallback.containsKey(file);
  }

  private void freeObject(@NotNull Object fileOrProject) {
    myDismissedObjects.remove(fileOrProject);
    CompletableFuture<CliResult> future = myFileOrProjectToCallback.remove(fileOrProject);
    if (future == null) {
      return;
    }
    future.complete(CliResult.OK);
  }

  static final class MyNotification extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
    private static final Key<EditorNotificationPanel> KEY = Key.create("CommandLineWaitingNotification");

    @Override
    public @NotNull Key<EditorNotificationPanel> getKey() {
      return KEY;
    }

    @Override
    public @Nullable EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
      if (!PropertiesComponent.getInstance().getBoolean(DO_NOT_SHOW_KEY, false)) {
        CommandLineWaitingManager manager = ApplicationManager.getApplication().getServiceIfCreated(CommandLineWaitingManager.class);
        if (manager != null && manager.hasHookFor(file) && !manager.myDismissedObjects.contains(file)) {
          return new MyNotificationPanel(file);
        }
      }
      return null;
    }
  }

  private static final class MyNotificationPanel extends EditorNotificationPanel {
    private MyNotificationPanel(@NotNull VirtualFile virtualFile) {
      super(EditorColors.GUTTER_BACKGROUND);
      setText(IdeBundle.message("activation.file.is.waiting.title"));

      createActionLabel(IdeBundle.message("activation.file.is.waiting.release"), () -> {
        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        ApplicationManager.getApplication().runWriteAction(() -> {
          if (document != null) {
            FileDocumentManager.getInstance().saveDocument(document);
          }
          else {
            FileDocumentManager.getInstance().saveAllDocuments();
          }
        });

        getInstance().freeObject(virtualFile);
        EditorNotifications.updateAll();
      });
      createActionLabel(IdeBundle.message("activation.file.is.waiting.do.not.show"), () -> {
        PropertiesComponent.getInstance().setValue(DO_NOT_SHOW_KEY, true);
        EditorNotifications.updateAll();
      });
      createActionLabel(IdeBundle.message("activation.file.is.waiting.dismiss"), () -> {
        getInstance().myDismissedObjects.add(virtualFile);
        EditorNotifications.updateAll();
      });
    }
  }
}
