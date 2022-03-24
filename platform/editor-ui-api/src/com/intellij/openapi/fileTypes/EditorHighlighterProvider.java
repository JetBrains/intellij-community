// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  Low level API for customizing language's file syntax highlighting in editor component.
 *  Override to provide {@link EditorHighlighter} which creates {@link HighlighterIterator}
 *  which returns a stream of {@link com.intellij.psi.tree.IElementType}s with their {@link com.intellij.openapi.editor.markup.TextAttributes}
 *
 *  For higher-level highlighting API see {@link com.intellij.codeInspection.LocalInspectionTool} or {@link com.intellij.lang.annotation.Annotator}
 */
public interface EditorHighlighterProvider {
  /**
   * Low level API for customizing language's file syntax highlighting in editor component.
   *
   * @param project     The project in which the highlighter will work, or null if the highlighter is not tied to any project.
   * @param fileType    the file type of the file to be highlighted
   * @param virtualFile The file to be highlighted
   * @param colors      color scheme highlighter shall be initialized with.   @return EditorHighlighter implementation
   */
  EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                         @NotNull FileType fileType,
                                         @Nullable final VirtualFile virtualFile,
                                         @NotNull EditorColorsScheme colors);
}
