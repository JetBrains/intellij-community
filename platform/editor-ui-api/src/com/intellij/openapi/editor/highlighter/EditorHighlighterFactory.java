/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class EditorHighlighterFactory {

  public static EditorHighlighterFactory getInstance() {
    return ServiceManager.getService(EditorHighlighterFactory.class);
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