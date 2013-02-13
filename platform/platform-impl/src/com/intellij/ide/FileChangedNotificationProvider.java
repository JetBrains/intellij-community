/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileChangedNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("file.changed.notification.panel");

  private final Project myProject;

  public FileChangedNotificationProvider(@NotNull Project project, @NotNull FrameStateManager frameStateManager) {
    myProject = project;

    frameStateManager.addListener(new FrameStateListener.Adapter() {
      @Override
      public void onFrameActivated() {
        if (!myProject.isDisposed() && !GeneralSettings.getInstance().isSyncOnFrameActivation()) {
          EditorNotifications notifications = EditorNotifications.getInstance(myProject);
          for (VirtualFile file : FileEditorManager.getInstance(myProject).getSelectedFiles()) {
            notifications.updateNotifications(file);
          }
        }
      }
    }, project);
  }

  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!myProject.isDisposed() && !GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      VirtualFileSystem fs = file.getFileSystem();
      if (fs instanceof LocalFileSystem) {
        FileAttributes attributes = ((LocalFileSystem)fs).getAttributes(file);
        if (attributes == null || file.getTimeStamp() != attributes.lastModified || file.getLength() != attributes.length) {
          return createPanel(file);
        }
      }
    }

    return null;
  }

  private EditorNotificationPanel createPanel(@NotNull final VirtualFile file) {
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(IdeBundle.message("file.changed.externally.message"));
    panel.createActionLabel(IdeBundle.message("file.changed.externally.reload"), new Runnable() {
      @Override
      public void run() {
        if (!myProject.isDisposed()) {
          RefreshQueue.getInstance().refresh(false, false, null, file);
          EditorNotifications.getInstance(myProject).updateNotifications(file);
        }
      }
    });
    return panel;
  }
}
