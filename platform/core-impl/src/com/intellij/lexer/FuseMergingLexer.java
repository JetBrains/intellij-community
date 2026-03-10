// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/// Lexer merging a set of token into another type
/// Useful if you want your {@link LayeredLexer} lexers to process large segments of text
///
/// @see MergingLexerAdapter to fuse tokens of the same type
public final class FuseMergingLexer extends MergingLexerAdapterBase {
  private final IElementType fusedTokenType;
  private final TokenSet tokensToFuse;
  private final MergeFunction mergeFunction = new Merger();

  public FuseMergingLexer(Lexer original, IElementType fusedTokenType, TokenSet tokensToFuse) {
    super(original);
    this.fusedTokenType = fusedTokenType;
    this.tokensToFuse = tokensToFuse;
  }

  @Override
  public MergeFunction getMergeFunction() {
    return mergeFunction;
  }

  private class Merger implements MergeFunction {
    private boolean shouldMerge(IElementType type) {
      return fusedTokenType == type || tokensToFuse.contains(type);
    }

    @Override
    public IElementType merge(IElementType type, Lexer originalLexer) {
      if (!shouldMerge(type)) {
        return type;
      }

      while (true) {
        IElementType tokenType = originalLexer.getTokenType();
        if (!shouldMerge(tokenType)) break;
        originalLexer.advance();
      }
      return fusedTokenType;
    }
  }
}
