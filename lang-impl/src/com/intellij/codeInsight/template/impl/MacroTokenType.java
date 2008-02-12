package com.intellij.codeInsight.template.impl;

import com.intellij.lang.Language;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/**
 * @author yole
 */
public interface MacroTokenType {
  IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
  IElementType IDENTIFIER = new IElementType("IDENTIFIER", Language.ANY);
  IElementType STRING_LITERAL = new IElementType("STRING_LITERAL", Language.ANY);
  IElementType LPAREN = new IElementType("LPAREN", Language.ANY);
  IElementType RPAREN = new IElementType("RPAREN", Language.ANY);
  IElementType COMMA = new IElementType("COMMA", Language.ANY);
  IElementType EQ = new IElementType("EQ", Language.ANY);
}
