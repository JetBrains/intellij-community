/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lexer;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * @author yole
 */
public class JavaDocTokenTypes implements DocCommentTokenTypes {
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
