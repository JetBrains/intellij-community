// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public final class LargeFileRegexSearchNotificationProvider implements EditorNotificationProvider {
  private static final Key<String> HIDDEN_KEY = Key.create("large.file.editor.regex.search.notification.hidden");

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    return fileEditor -> {
      if (!(fileEditor instanceof LargeFileEditor largeFileEditor)) return null;

      Editor editor = largeFileEditor.getEditor();

      if (editor.getUserData(HIDDEN_KEY) != null) return null;

      if (!largeFileEditor.getSearchManager().canShowRegexSearchWarning()) return null;

      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
      panel.createActionLabel(EditorBundle.message("notification.hide.message"), () -> {
        editor.putUserData(HIDDEN_KEY, "true");
        update(file, project);
      });

      return panel.text(EditorBundle.message("message.warning.about.regex.search.limitations", String.valueOf(
        // 2 pages "*2" and bytes to Kbytes "/1000" gives "/500" (see regex search realization specificities)
        largeFileEditor.getPageSize() / 500
      )));
    };
  }

  private static void update(@NotNull VirtualFile file, @NotNull Project project) {
    EditorNotifications.getInstance(project).updateNotifications(file);
  }
}
