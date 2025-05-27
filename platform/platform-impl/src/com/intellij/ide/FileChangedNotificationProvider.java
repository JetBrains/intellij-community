// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Function;

@ApiStatus.Internal
public final class FileChangedNotificationProvider implements EditorNotificationProvider, DumbAware {
  private static final Logger LOG = Logger.getInstance(FileChangedNotificationProvider.class);

  public FileChangedNotificationProvider() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();

    connection.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
      @Override
      public void applicationActivated(@NotNull IdeFrame ideFrame) {
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

    connection.subscribe(VirtualFileManager.VFS_CHANGES_BG, new BulkFileListenerBackgroundable() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
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

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    if (project.isDisposed() || GeneralSettings.getInstance().isSyncOnFrameActivation()) return null;

    VirtualFileSystem fs = file.getFileSystem();
    if (!(fs instanceof LocalFileSystem)) return null;

    FileAttributes attributes = ((LocalFileSystem)fs).getAttributes(file);
    if (attributes != null && file.getTimeStamp() == attributes.lastModified && file.getLength() == attributes.length) return null;

    return fileEditor -> {
      if (LOG.isDebugEnabled()) LOG.debug(String.format("%s: (%s,%s) -> %s", file, file.getTimeStamp(), file.getLength(), attributes));
      return createPanel(file, fileEditor, project);
    };
  }

  private static @NotNull EditorNotificationPanel createPanel(final @NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
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
