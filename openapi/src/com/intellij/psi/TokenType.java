package com.intellij.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.Language;

public interface TokenType {
  IElementType WHITE_SPACE = new IElementType("WHITE_SPACE", Language.ANY);
  IElementType BAD_CHARACTER = new IElementType("BAD_CHARACTER", Language.ANY);

  IElementType NEW_LINE_INDENT = new IElementType("NEW_LINE_INDENT", Language.ANY);
}
