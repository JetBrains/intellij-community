package com.intellij.lexer;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * @author yole
 */
public class JavaDocTokenTypes implements DocCommentTokenTypes {
  public IElementType commentStart() {
    return JavaDocTokenType.DOC_COMMENT_START;
  }

  public IElementType commentEnd() {
    return JavaDocTokenType.DOC_COMMENT_END;
  }

  public IElementType commentData() {
    return JavaDocTokenType.DOC_COMMENT_DATA;
  }

  public IElementType space() {
    return JavaDocTokenType.DOC_SPACE;
  }

  public IElementType tagValueToken() {
    return JavaDocTokenType.DOC_TAG_VALUE_TOKEN;
  }

  public IElementType tagValueLParen() {
    return JavaDocTokenType.DOC_TAG_VALUE_LPAREN;
  }

  public IElementType tagValueRParen() {
    return JavaDocTokenType.DOC_TAG_VALUE_RPAREN;
  }

  public IElementType tagValueSharp() {
    return JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN;
  }

  public IElementType tagValueComma() {
    return JavaDocTokenType.DOC_TAG_VALUE_COMMA;
  }

  public IElementType tagName() {
    return JavaDocTokenType.DOC_TAG_NAME;
  }

  public IElementType tagValueLT() {
    return JavaDocTokenType.DOC_TAG_VALUE_LT;
  }

  public IElementType tagValueGT() {
    return JavaDocTokenType.DOC_TAG_VALUE_GT;
  }

  public IElementType inlineTagStart() {
    return JavaDocTokenType.DOC_INLINE_TAG_START;
  }

  public IElementType inlineTagEnd() {
    return JavaDocTokenType.DOC_INLINE_TAG_END;
  }

  public IElementType badCharacter() {
    return JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER;
  }

  public IElementType commentLeadingAsterisks() {
    return JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
  }
}
