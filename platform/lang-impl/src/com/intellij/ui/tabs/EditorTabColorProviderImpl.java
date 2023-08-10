// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FileColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class EditorTabColorProviderImpl implements EditorTabColorProvider, DumbAware {
  @Override
  public @Nullable Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file) {
    FileColorManager colorManager = FileColorManager.getInstance(project);
    return colorManager.isEnabledForTabs() ? colorManager.getFileColor(file) : null;
  }

  @Override
  public @NotNull ColorKey getEditorTabForegroundColor(@NotNull Project project, @NotNull VirtualFile file) {
    return FileStatusManager.getInstance(project).getStatus(file).getColorKey();
  }

  @Override
  public @Nullable Color getProjectViewColor(@NotNull Project project, @NotNull VirtualFile file) {
    FileColorManager colorManager = FileColorManager.getInstance(project);
    return colorManager.isEnabledForProjectView() ? colorManager.getFileColor(file) : null;
  }
}
