// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the brace matching support required for a custom language. For paired
 * brace matching to work, the language must also provide a
 * {@link com.intellij.openapi.fileTypes.SyntaxHighlighter} and return the correct
 * lexer from {@code getHighlightingLexer()}.
 *
 * @author max
 * @see LanguageBraceMatching
 * @see BracePair
 * @see com.intellij.codeInsight.highlighting.BraceMatcherTerminationAspect
 */
public interface PairedBraceMatcher {
  /**
   * Returns the array of definitions for brace pairs that need to be matched when
   * editing code in the language.
   *
   * @return the array of brace pair definitions.
   */
  BracePair @NotNull [] getPairs();

  /**
   * Returns {@code true} if paired rbrace should be inserted after lbrace of given type when lbrace is encountered before contextType token.
   * It is safe to always return {@code true}, then paired brace will be inserted anyway.
   *
   * @param lbraceType  lbrace for which information is queried
   * @param contextType token type that follows lbrace
   * @return true / false as described
   */
  boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType);

  /**
   * Returns the start offset of the code construct which owns the opening structural brace at the specified offset. For example,
   * if the opening brace belongs to an 'if' statement, returns the start offset of the 'if' statement.
   *
   * @param file               the file in which brace matching is performed.
   * @param openingBraceOffset the offset of an opening structural brace.
   * @return the offset of corresponding code construct, or the same offset if not defined.
   */
  int getCodeConstructStart(final PsiFile file, int openingBraceOffset);
}
