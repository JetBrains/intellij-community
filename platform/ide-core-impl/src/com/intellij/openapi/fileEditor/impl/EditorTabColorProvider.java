// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Allows highlighting of file-related renderers and editor tabs with custom background colors.
 */
public interface EditorTabColorProvider {
  ExtensionPointName<EditorTabColorProvider> EP_NAME = ExtensionPointName.create("com.intellij.editorTabColorProvider");

  /**
   *
   * @param project current IDE project.
   * @param file a file you need to highlight.
   * @return background color to highlight editor tab.
   * @see EditorTabPresentationUtil#getEditorTabBackgroundColor(Project, VirtualFile)
   */
  @Nullable
  Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file);

  /**
   *
   * @param project current IDE project.
   * @param file a file you need to highlight.
   * @return background color to highlight file row in trees and lists.
   * @see EditorTabPresentationUtil#getFileBackgroundColor(Project, VirtualFile)
   */
  @Nullable
  default Color getProjectViewColor(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }
}
