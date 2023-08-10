// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public abstract @NotNull EditorHighlighter createEditorHighlighter(final SyntaxHighlighter syntaxHighlighter, @NotNull EditorColorsScheme colors);

  public abstract @NotNull EditorHighlighter createEditorHighlighter(@NotNull FileType fileType, @NotNull EditorColorsScheme settings, final Project project);

  public abstract @NotNull EditorHighlighter createEditorHighlighter(final Project project, @NotNull FileType fileType);

  public abstract @NotNull EditorHighlighter createEditorHighlighter(final @NotNull VirtualFile file, @NotNull EditorColorsScheme globalScheme, final @Nullable Project project);

  public abstract @NotNull EditorHighlighter createEditorHighlighter(final Project project, @NotNull VirtualFile file);

  public abstract @NotNull EditorHighlighter createEditorHighlighter(final Project project, @NotNull String fileName);

  public abstract @NotNull EditorHighlighter createEditorHighlighter(@NotNull EditorColorsScheme settings, @NotNull String fileName, final @Nullable Project project);
}