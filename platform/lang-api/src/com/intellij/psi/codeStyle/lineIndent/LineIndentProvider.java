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
 * An interface for indentation calculation. Used in editor actions like Enter handling.
 */
public interface LineIndentProvider {
  
  /**
   * Calculates the indent that should be used for the line at specified offset in the specified
   * document.
   *
   * @param project  The current project.
   * @param editor   The current editor.
   * @param language Context language to be used at the current offset.
   * @param offset   The caret offset in the editor.
   * @return The indent string (possibly consisting of tabs and/or white spaces), or null if
   * LineIndentProvider can't calculate the indent (in this case no indent adjustment will be made).
   */
  @Nullable
  String getLineIndent(@NotNull Project project, @NotNull Editor editor, Language language, int offset);
  
  boolean isSuitableFor(@Nullable Language language);
}
