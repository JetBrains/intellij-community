// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  int adjustLineIndent(final @NotNull Document document, final int offset, FormattingMode mode);
  
  FormattingMode getCurrentFormattingMode();
}
