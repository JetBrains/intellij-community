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
package com.intellij.psi.codeStyle;

import com.intellij.formatting.FormattingMode;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

/**
 * Used to differentiate between line indent adjustment on a new line after Enter and explicit indent adjustment action.
 * 
 * @see FormattingMode
 * @see CodeStyleManager
 */
public interface FormattingModeAwareIndentAdjuster {

  /**
   * Adjust line indent at document offset using {@code mode}.
   * 
   * @param document The document to adjust line indent in.
   * @param offset   The offset in the document.
   * @param mode     The mode: {@link FormattingMode#ADJUST_INDENT} or {@link FormattingMode#ADJUST_INDENT_ON_ENTER}
   * @return Adjusted offset.
   */
  int adjustLineIndent(@NotNull final Document document, final int offset, FormattingMode mode);
  
  FormattingMode getCurrentFormattingMode();
}
