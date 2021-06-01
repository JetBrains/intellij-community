  // Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface EditorHighlighterProvider {
  /**
   * Lower level API for customizing language's file syntax highlighting in editor component.
   *
   * @param project The project in which the highlighter will work, or null if the highlighter is not tied to any project.
   * @param fileType the file type of the file to be highlighted
   * @param virtualFile The file to be highlighted
   * @param colors color scheme highlighter shall be initialized with.   @return EditorHighlighter implementation
   */

  EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                         @NotNull FileType fileType,
                                         @Nullable final VirtualFile virtualFile,
                                         @NotNull EditorColorsScheme colors);
}
