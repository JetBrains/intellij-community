// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

import static com.google.common.base.MoreObjects.firstNonNull;

final class GeneratedFileEditingNotificationProvider implements EditorNotificationProvider, DumbAware {
  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    var matchingFilter = GeneratedSourcesFilter.findFirstMatchingFilterNonBlocking(file, project);

    if (matchingFilter == null) return null;

    String notificationText = firstNonNull(
      matchingFilter.getNotificationText(file, project),
      LangBundle.message("link.label.generated.source.files")
    );

    return fileEditor -> {
      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
      panel.setText(notificationText);
      return panel;
    };
  }
}
