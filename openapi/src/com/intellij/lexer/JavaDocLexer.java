package com.intellij.lexer;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class JavaDocLexer extends MergingLexerAdapter {
  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(new IElementType[]{
    JavaDocTokenType.DOC_COMMENT_DATA,
    JavaDocTokenType.DOC_SPACE
  });

  public JavaDocLexer() {
    super(new _JavaDocLexer(), TOKENS_TO_MERGE);
  }
}