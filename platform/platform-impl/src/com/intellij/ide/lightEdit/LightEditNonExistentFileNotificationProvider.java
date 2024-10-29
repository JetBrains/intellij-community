// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

@ApiStatus.Internal
public final class LightEditNonExistentFileNotificationProvider implements EditorNotificationProvider, DumbAware {
  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    if (!LightEdit.owns(project)) return null;
    @NlsSafe String creationMessage = file.getUserData(LightEditUtil.CREATION_MESSAGE);
    if (creationMessage == null) return null;

    return fileEditor -> {
      EditorNotificationPanel notificationPanel =
        new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Error).text(creationMessage);
      notificationPanel.createActionLabel(ApplicationBundle.message("light.edit.file.creation.failed.hide.message"), () -> {
        file.putUserData(LightEditUtil.CREATION_MESSAGE, null);
        EditorNotifications.getInstance(project).updateNotifications(file);
      });

      return notificationPanel;
    };
  }
}
