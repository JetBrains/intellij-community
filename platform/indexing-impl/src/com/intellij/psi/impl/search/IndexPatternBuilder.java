// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 *
 * @see com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
 */
public interface IndexPatternBuilder {
  ExtensionPointName<IndexPatternBuilder> EP_NAME = ExtensionPointName.create("com.intellij.indexPatternBuilder");

  @Nullable Lexer getIndexingLexer(@NotNull PsiFile file);

  @Nullable TokenSet getCommentTokenSet(@NotNull PsiFile file);

  int getCommentStartDelta(IElementType tokenType);
  int getCommentEndDelta(IElementType tokenType);

  /**
   * Characters (in addition to whitespace) which can be present in the indent section of pattern occurrence's continuation
   * on subsequent line
   */
  default @NotNull String getCharsAllowedInContinuationPrefix(@NotNull IElementType tokenType) {
    return "";
  }

  default int getCommentStartDelta(@NotNull IElementType tokenType, @NotNull CharSequence tokenText) {
    return getCommentStartDelta(tokenType);
  }
}
