// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FileChangedNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Logger LOG = Logger.getInstance(FileChangedNotificationProvider.class);
  private static final Key<EditorNotificationPanel> KEY = Key.create("file.changed.notification.panel");

  public FileChangedNotificationProvider() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();

    connection.subscribe(FrameStateListener.TOPIC, new FrameStateListener() {
      @Override
      public void onFrameActivated() {
        if (GeneralSettings.getInstance().isSyncOnFrameActivation()) {
          return;
        }

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          if (project.isDisposed()) {
            continue;
          }

          EditorNotifications notifications = EditorNotifications.getInstance(project);
          for (VirtualFile file : FileEditorManager.getInstance(project).getSelectedFiles()) {
            notifications.updateNotifications(file);
          }
        }
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        if (GeneralSettings.getInstance().isSyncOnFrameActivation()) {
          return;
        }

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          if (project.isDisposed()) {
            continue;
          }

          List<String> openFilePaths = ContainerUtil.map(FileEditorManager.getInstance(project).getSelectedFiles(), f -> f.getPath());
          EditorNotifications notifications = EditorNotifications.getInstance(project);
          for (VFileEvent event : events) {
            String path = event.getPath();
            if (openFilePaths.contains(path)) {
              VirtualFile file = event.getFile();
              if (file != null) {
                notifications.updateNotifications(file);
              }
            }
          }
        }
      }
    });
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (!project.isDisposed() && !GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      VirtualFileSystem fs = file.getFileSystem();
      if (fs instanceof LocalFileSystem) {
        FileAttributes attributes = ((LocalFileSystem)fs).getAttributes(file);
        if (attributes == null || file.getTimeStamp() != attributes.lastModified || file.getLength() != attributes.length) {
          LogUtil.debug(LOG, "%s: (%s,%s) -> %s", file, file.getTimeStamp(), file.getLength(), attributes);
          return createPanel(file, fileEditor, project);
        }
      }
    }

    return null;
  }

  @NotNull
  private static EditorNotificationPanel createPanel(@NotNull final VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor);
    panel.setText(IdeBundle.message("file.changed.externally.message"));
    panel.createActionLabel(IdeBundle.message("file.changed.externally.reload"), () -> {
      if (!project.isDisposed()) {
        file.refresh(false, false);
        EditorNotifications.getInstance(project).updateNotifications(file);
      }
    });
    return panel;
  }
}
