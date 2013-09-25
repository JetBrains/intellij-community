  /*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
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
