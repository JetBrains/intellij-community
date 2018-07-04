// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Represents the occurrence of an index pattern in the comments of a source code file.
 *
 * @author yole
 * @since 5.1
 * @see com.intellij.psi.search.searches.IndexPatternSearch
 * @see IndexPatternProvider
 */
public interface IndexPatternOccurrence {
  /**
   * Returns the file in which the occurrence was found.
   *
   * @return the file in which the occurrence was found.
   */
  @NotNull PsiFile getFile();

  /**
   * Returns the text range which was matched by the pattern.
   *
   * @return the text range which was matched by the pattern.
   */
  @NotNull TextRange getTextRange();

  /**
   * Additional ranges associated with matched range (e.g. for multi-line matching)
   */
  default @NotNull List<TextRange> getAdditionalTextRanges() {
    return Collections.emptyList();
  }

  /**
   * Returns the instance of the pattern which was matched.
   *
   * @return the instance of the pattern which was matched.
   */
  @NotNull IndexPattern getPattern();
}
