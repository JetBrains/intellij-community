// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileChooserDialog {
  DataKey<Boolean> PREFER_LAST_OVER_TO_SELECT = PathChooserDialog.PREFER_LAST_OVER_EXPLICIT;

  /** @deprecated use {@link #choose(Project, VirtualFile...)} instead */
  @Deprecated(forRemoval = true)
  default VirtualFile @NotNull [] choose(@Nullable VirtualFile toSelect, @Nullable Project project) {
    return toSelect != null ? choose(project, toSelect) : choose(project);
  }

  /**
   * Choose one or more files.
   *
   * @param project  use this project (you may pass {@code null} if you already set a project in the constructor/factory)
   * @param toSelect files to be pre-selected
   * @return files chosen by a user, or an empty array if the dialog was canceled
   */
  VirtualFile @NotNull [] choose(@Nullable Project project, VirtualFile @NotNull ... toSelect);
}
