// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class EditorHighlighterFactory {
  public static EditorHighlighterFactory getInstance() {
    return ApplicationManager.getApplication().getService(EditorHighlighterFactory.class);
  }

  @NotNull
  public abstract EditorHighlighter createEditorHighlighter(final SyntaxHighlighter syntaxHighlighter, @NotNull EditorColorsScheme colors);

  @NotNull
  public abstract EditorHighlighter createEditorHighlighter(@NotNull FileType fileType, @NotNull EditorColorsScheme settings, final Project project);

  @NotNull
  public abstract EditorHighlighter createEditorHighlighter(final Project project, @NotNull FileType fileType);

  @NotNull
  public abstract EditorHighlighter createEditorHighlighter(@NotNull final VirtualFile file, @NotNull EditorColorsScheme globalScheme, @Nullable final Project project);

  @NotNull
  public abstract EditorHighlighter createEditorHighlighter(final Project project, @NotNull VirtualFile file);

  @NotNull
  public abstract EditorHighlighter createEditorHighlighter(final Project project, @NotNull String fileName);

  @NotNull
  public abstract EditorHighlighter createEditorHighlighter(@NotNull EditorColorsScheme settings, @NotNull String fileName, @Nullable final Project project);
}