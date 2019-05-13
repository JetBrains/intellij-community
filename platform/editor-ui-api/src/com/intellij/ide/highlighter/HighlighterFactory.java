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
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class HighlighterFactory {
  private HighlighterFactory() {}

  @NotNull
  public static EditorHighlighter createHighlighter(SyntaxHighlighter highlighter, @NotNull EditorColorsScheme settings) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(highlighter, settings);
  }

  @NotNull
  public static EditorHighlighter createHighlighter(Project project, @NotNull String fileName) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileName);
  }

  @NotNull
  public static EditorHighlighter createHighlighter(Project project, @NotNull VirtualFile file) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file);
  }

  @NotNull
  public static EditorHighlighter createHighlighter(Project project, @NotNull FileType fileType) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType);
  }

  @NotNull
  public static EditorHighlighter createHighlighter(@NotNull EditorColorsScheme settings, @NotNull String fileName, Project project) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(settings, fileName, project);
  }

  @NotNull
  public static EditorHighlighter createHighlighter(@NotNull FileType fileType, @NotNull EditorColorsScheme settings, Project project) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, settings, project);
  }

  @NotNull
  public static EditorHighlighter createHighlighter(@NotNull VirtualFile vFile, @NotNull EditorColorsScheme settings, Project project) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(vFile, settings, project);
  }
}