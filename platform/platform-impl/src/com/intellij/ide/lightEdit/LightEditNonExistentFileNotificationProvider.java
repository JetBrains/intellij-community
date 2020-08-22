// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightEditNonExistentFileNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create(LightEditNonExistentFileNotificationProvider.class.getName());



  @Override
  public @NotNull Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    if (LightEdit.owns(project)) {
      String creationMessage = file.getUserData(LightEditUtil.CREATION_MESSAGE);
      if (creationMessage != null) {
        EditorNotificationPanel notificationPanel = new EditorNotificationPanel().text(creationMessage);
        notificationPanel.createActionLabel(ApplicationBundle.message("light.edit.file.creation.failed.hide.message"), () -> {
          file.putUserData(LightEditUtil.CREATION_MESSAGE, null);
          EditorNotifications.getInstance(project).updateNotifications(file);
        });
        return notificationPanel;
      }
    }
    return null;
  }
}
