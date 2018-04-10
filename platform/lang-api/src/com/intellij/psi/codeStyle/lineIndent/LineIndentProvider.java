/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.lineIndent;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An interface for fast indentation calculation as an alternative to formatter-based logic. Used in editor actions like Enter handling.
 * Assumes that indent string is constructed using the editor document {@code editor.getDocument()} and the current offset without
 * any document commits and PSI tree rebuilding which can be a time consuming operation. If there is no {@code LineIndentProvider} for the
 * current editor and language context OR if a line indent provider can't calculate the indent (returns {@code null}), the document is
 * committed and a formatter-based line indent calculation is performed.
 */
public interface LineIndentProvider {
  /**
   * Marker object to indicate that no further formatter-based indent adjustment must be made.
   */
  String DO_NOT_ADJUST = new String();
  
  /**
   * Calculates the indent that should be used for the line at specified offset in the specified
   * document.
   *
   * @param project  The current project.
   * @param editor   The current editor.
   * @param language Context language to be used at the current offset.
   * @param offset   The caret offset in the editor.
   * @return The indent string (possibly consisting of tabs and/or white spaces), {@code null} if LineIndentProvider can't calculate the
   * indent (in this case indent calculation is delegated to formatter if smart indent mode is used) or {@link #DO_NOT_ADJUST} constant to
   * leave the current caret position as is without any further formatter-based adjustment.
   */
  @Nullable
  String getLineIndent(@NotNull Project project, @NotNull Editor editor, Language language, int offset);
  
  boolean isSuitableFor(@Nullable Language language);
}
