// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType

/**
 * @see com.intellij.psi.JavaDocTokenType
 */
object JavaDocSyntaxTokenType {
  @JvmField val DOC_COMMENT_START: SyntaxElementType = SyntaxElementType("DOC_COMMENT_START")
  @JvmField val DOC_COMMENT_END: SyntaxElementType = SyntaxElementType("DOC_COMMENT_END")
  @JvmField val DOC_COMMENT_DATA: SyntaxElementType = SyntaxElementType("DOC_COMMENT_DATA")
  @JvmField val DOC_SPACE: SyntaxElementType = SyntaxElementType("DOC_SPACE")
  @JvmField val DOC_COMMENT_LEADING_ASTERISKS: SyntaxElementType = SyntaxElementType("DOC_COMMENT_LEADING_ASTERISKS")
  @JvmField val DOC_TAG_NAME: SyntaxElementType = SyntaxElementType("DOC_TAG_NAME")
  @JvmField val DOC_INLINE_TAG_START: SyntaxElementType = SyntaxElementType("DOC_INLINE_TAG_START")
  @JvmField val DOC_INLINE_TAG_END: SyntaxElementType = SyntaxElementType("DOC_INLINE_TAG_END")

  @JvmField val DOC_TAG_VALUE_TOKEN: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_TOKEN")
  @JvmField val DOC_TAG_VALUE_DOT: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_DOT")
  @JvmField val DOC_TAG_VALUE_COMMA: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_COMMA")
  @JvmField val DOC_TAG_VALUE_LPAREN: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_LPAREN")
  @JvmField val DOC_TAG_VALUE_RPAREN: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_RPAREN")
  @JvmField val DOC_TAG_VALUE_QUOTE: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_QUOTE")
  @JvmField val DOC_TAG_VALUE_COLON: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_COLON")
  @JvmField val DOC_TAG_ATTRIBUTE_NAME: SyntaxElementType = SyntaxElementType("DOC_TAG_ATTRIBUTE_NAME")
  @JvmField val DOC_TAG_ATTRIBUTE_VALUE: SyntaxElementType = SyntaxElementType("DOC_TAG_ATTRIBUTE_VALUE")
  @JvmField val DOC_TAG_VALUE_LT: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_LT")
  @JvmField val DOC_TAG_VALUE_GT: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_GT")
  @JvmField val DOC_TAG_VALUE_SLASH: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_SLASH")
  @JvmField val DOC_TAG_VALUE_SHARP_TOKEN: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_SHARP_TOKEN")

  // Additional tokens for java 23 markdown
  @JvmField val DOC_CODE_FENCE: SyntaxElementType = SyntaxElementType("DOC_CODE_FENCE")
  @JvmField val DOC_RBRACKET: SyntaxElementType = SyntaxElementType("DOC_RBRACKET")
  @JvmField val DOC_LBRACKET: SyntaxElementType = SyntaxElementType("DOC_LBRACKET")
  @JvmField val DOC_LPAREN: SyntaxElementType = SyntaxElementType("DOC_LPAREN")
  @JvmField val DOC_RPAREN: SyntaxElementType = SyntaxElementType("DOC_RPAREN")
  @JvmField val DOC_SHARP: SyntaxElementType = SyntaxElementType("DOC_SHARP")
  @JvmField val DOC_INLINE_CODE_FENCE: SyntaxElementType = SyntaxElementType("DOC_INLINE_CODE_FENCE")
  @JvmField val DOC_COMMA: SyntaxElementType = SyntaxElementType("DOC_COMMA")

  @JvmField val DOC_COMMENT_BAD_CHARACTER: SyntaxElementType = SyntaxElementType("DOC_COMMENT_BAD_CHARACTER")
}