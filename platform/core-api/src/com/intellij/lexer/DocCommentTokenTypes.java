// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Allows to specify a set of language-dependent token types for the doc comment lexer.
 */
public interface DocCommentTokenTypes {
  IElementType commentStart();
  IElementType commentEnd();
  IElementType commentData();
  TokenSet spaceCommentsTokenSet();
  IElementType space();
  IElementType tagValueToken();
  IElementType tagValueLParen();
  IElementType tagValueRParen();
  IElementType tagValueSharp();
  IElementType tagValueComma();
  IElementType tagName();
  IElementType tagValueLT();
  IElementType tagValueGT();
  IElementType inlineTagStart();
  IElementType inlineTagEnd();
  IElementType badCharacter();
  IElementType commentLeadingAsterisks();
  default IElementType tagValueQuote() {
    return commentData();
  }
  default IElementType tagValueColon() {
    return commentData();
  }
}
