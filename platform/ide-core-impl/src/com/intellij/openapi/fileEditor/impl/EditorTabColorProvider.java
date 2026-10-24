// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

/**
 * Allows highlighting of file-related renderers and editor tabs with custom background colors.
 */
public interface EditorTabColorProvider extends PossiblyDumbAware {
  ExtensionPointName<EditorTabColorProvider> EP_NAME = ExtensionPointName.create("com.intellij.editorTabColorProvider");

  /**
   * Return this instance to give the file no color and to skip the later providers.
   * A {@code null} result asks the next provider instead.
   * Only this instance has the effect, compared by identity. It is never painted.
   */
  @ApiStatus.Internal
  @SuppressWarnings("UseJBColor")
  Color NO_COLOR = new Color(0, 0, 0, 0);

  /**
   *
   * @param project current IDE project.
   * @param file a file you need to highlight.
   * @return background color to highlight editor tab, or {@link #NO_COLOR}.
   * @see EditorTabPresentationUtil#getEditorTabBackgroundColor(Project, VirtualFile)
   */
  @Nullable
  Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file);

  /**
   *
   * @param project current IDE project.
   * @param file a file you need to highlight.
   * @return tab foreground color for the file.
   */
  @ApiStatus.Experimental
  default @Nullable ColorKey getEditorTabForegroundColor(@NotNull Project project, @NotNull VirtualFile file) { return null; }

  /**
   *
   * @param project current IDE project.
   * @param file a file you need to highlight.
   * @return background color to highlight file row in trees and lists, or {@link #NO_COLOR}.
   * @see EditorTabPresentationUtil#getFileBackgroundColor(Project, VirtualFile)
   */
  default @Nullable Color getProjectViewColor(@NotNull Project project, @NotNull VirtualFile file) {
    return null;
  }
}
