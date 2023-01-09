// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public final class LargeFileNotificationProvider implements EditorNotificationProvider {
  private static final Key<String> HIDDEN_KEY = Key.create("large.file.editor.notification.hidden");
  private static final String DISABLE_KEY = "large.file.editor.notification.disabled";

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    return fileEditor -> {
      if (!(fileEditor instanceof LargeFileEditor)) return null;
      Editor editor = ((LargeFileEditor)fileEditor).getEditor();
      if (editor.getUserData(HIDDEN_KEY) != null || PropertiesComponent.getInstance().isTrueValue(DISABLE_KEY)) {
        return null;
      }

      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
      panel.createActionLabel(EditorBundle.message("notification.hide.message"), () -> {
        editor.putUserData(HIDDEN_KEY, "true");
        update(file, project);
      });
      panel.createActionLabel(EditorBundle.message("notification.dont.show.again.message"), () -> {
        PropertiesComponent.getInstance().setValue(DISABLE_KEY, "true");
        update(file, project);
      });
      return panel.text(EditorBundle.message("large.file.editor.notification.text.the.file.is.too.large.so.showing.in.read.only.mode",
                                             StringUtil.formatFileSize(file.getLength())));
    };
  }

  private static void update(@NotNull VirtualFile file, @NotNull Project project) {
    EditorNotifications.getInstance(project).updateNotifications(file);
  }
}
