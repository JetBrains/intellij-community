package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

/**
 * Allows to specify a set of language-dependent token types for the doc comment lexer.
 *
 * @author yole
 */
public interface DocCommentTokenTypes {
  IElementType commentStart();
  IElementType commentEnd();
  IElementType commentData();
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
}
