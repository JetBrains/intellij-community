// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

final class GeneratedFileEditingNotificationProvider implements EditorNotificationProvider, DumbAware {
  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    if (!GeneratedSourceFileChangeTracker.getInstance(project).isEditedGeneratedFile(file)) return null;

    String notificationText = getText(file, project);

    return fileEditor -> {
      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
      panel.setText(notificationText);
      return panel;
    };
  }

  private static @NlsContexts.LinkLabel @NotNull String getText(@NotNull VirtualFile file, @NotNull Project project) {
    if (!project.isDisposed() && file.isValid()) {
      for (GeneratedSourcesFilter filter : GeneratedSourcesFilter.EP_NAME.getExtensionList()) {
        if (!filter.isGeneratedSource(file, project)) {
          continue;
        }
        String text = filter.getNotificationText(file, project);
        if (text != null) {
          return text;
        }
      }
    }
    return LangBundle.message("link.label.generated.source.files");
  }
}
