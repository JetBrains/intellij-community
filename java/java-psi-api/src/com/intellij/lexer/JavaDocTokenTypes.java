// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public final class JavaDocTokenTypes implements DocCommentTokenTypes {
  public static final DocCommentTokenTypes INSTANCE = new JavaDocTokenTypes();
  private final TokenSet mySpaceCommentsSet = TokenSet.create(JavaDocTokenType.DOC_SPACE, JavaDocTokenType.DOC_COMMENT_DATA);

  private JavaDocTokenTypes() { }

  @Override
  public IElementType commentStart() {
    return JavaDocTokenType.DOC_COMMENT_START;
  }

  @Override
  public IElementType commentEnd() {
    return JavaDocTokenType.DOC_COMMENT_END;
  }

  @Override
  public IElementType commentData() {
    return JavaDocTokenType.DOC_COMMENT_DATA;
  }

  @Override
  public TokenSet spaceCommentsTokenSet() {
    return mySpaceCommentsSet;
  }

  @Override
  public IElementType space() {
    return JavaDocTokenType.DOC_SPACE;
  }

  @Override
  public IElementType tagValueToken() {
    return JavaDocTokenType.DOC_TAG_VALUE_TOKEN;
  }

  @Override
  public IElementType tagValueLParen() {
    return JavaDocTokenType.DOC_TAG_VALUE_LPAREN;
  }

  @Override
  public IElementType tagValueRParen() {
    return JavaDocTokenType.DOC_TAG_VALUE_RPAREN;
  }

  @Override
  public IElementType tagValueQuote() {
    return JavaDocTokenType.DOC_TAG_VALUE_QUOTE;
  }

  @Override
  public IElementType tagValueColon() {
    return JavaDocTokenType.DOC_TAG_VALUE_COLON;
  }

  @Override
  public IElementType tagValueSharp() {
    return JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN;
  }

  @Override
  public IElementType tagValueComma() {
    return JavaDocTokenType.DOC_TAG_VALUE_COMMA;
  }

  @Override
  public IElementType tagName() {
    return JavaDocTokenType.DOC_TAG_NAME;
  }

  @Override
  public IElementType tagValueLT() {
    return JavaDocTokenType.DOC_TAG_VALUE_LT;
  }

  @Override
  public IElementType tagValueGT() {
    return JavaDocTokenType.DOC_TAG_VALUE_GT;
  }

  @Override
  public IElementType inlineTagStart() {
    return JavaDocTokenType.DOC_INLINE_TAG_START;
  }

  @Override
  public IElementType inlineTagEnd() {
    return JavaDocTokenType.DOC_INLINE_TAG_END;
  }

  @Override
  public IElementType badCharacter() {
    return JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER;
  }

  @Override
  public IElementType commentLeadingAsterisks() {
    return JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
  }
}
