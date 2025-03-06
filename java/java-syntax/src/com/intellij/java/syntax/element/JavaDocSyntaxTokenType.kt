// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType

/**
 * @see com.intellij.psi.JavaDocTokenType
 */
object JavaDocSyntaxTokenType {
  val DOC_COMMENT_START: SyntaxElementType = SyntaxElementType("DOC_COMMENT_START")
  val DOC_COMMENT_END: SyntaxElementType = SyntaxElementType("DOC_COMMENT_END")
  val DOC_COMMENT_DATA: SyntaxElementType = SyntaxElementType("DOC_COMMENT_DATA")
  val DOC_SPACE: SyntaxElementType = SyntaxElementType("DOC_SPACE")
  val DOC_COMMENT_LEADING_ASTERISKS: SyntaxElementType = SyntaxElementType("DOC_COMMENT_LEADING_ASTERISKS")
  val DOC_TAG_NAME: SyntaxElementType = SyntaxElementType("DOC_TAG_NAME")
  val DOC_INLINE_TAG_START: SyntaxElementType = SyntaxElementType("DOC_INLINE_TAG_START")
  val DOC_INLINE_TAG_END: SyntaxElementType = SyntaxElementType("DOC_INLINE_TAG_END")

  val DOC_TAG_VALUE_TOKEN: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_TOKEN")
  val DOC_TAG_VALUE_DOT: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_DOT")
  val DOC_TAG_VALUE_COMMA: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_COMMA")
  val DOC_TAG_VALUE_LPAREN: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_LPAREN")
  val DOC_TAG_VALUE_RPAREN: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_RPAREN")
  val DOC_TAG_VALUE_QUOTE: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_QUOTE")
  val DOC_TAG_VALUE_COLON: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_COLON")
  val DOC_TAG_ATTRIBUTE_NAME: SyntaxElementType = SyntaxElementType("DOC_TAG_ATTRIBUTE_NAME")
  val DOC_TAG_ATTRIBUTE_VALUE: SyntaxElementType = SyntaxElementType("DOC_TAG_ATTRIBUTE_VALUE")
  val DOC_TAG_VALUE_LT: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_LT")
  val DOC_TAG_VALUE_GT: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_GT")
  val DOC_TAG_VALUE_SHARP_TOKEN: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_SHARP_TOKEN")

  // Additional tokens for java 23 markdown
  val DOC_CODE_FENCE: SyntaxElementType = SyntaxElementType("DOC_CODE_FENCE")
  val DOC_RBRACKET: SyntaxElementType = SyntaxElementType("DOC_RBRACKET")
  val DOC_LBRACKET: SyntaxElementType = SyntaxElementType("DOC_LBRACKET")
  val DOC_LPAREN: SyntaxElementType = SyntaxElementType("DOC_LPAREN")
  val DOC_RPAREN: SyntaxElementType = SyntaxElementType("DOC_RPAREN")
  val DOC_SHARP: SyntaxElementType = SyntaxElementType("DOC_SHARP")
  val DOC_INLINE_CODE_FENCE: SyntaxElementType = SyntaxElementType("DOC_INLINE_CODE_FENCE")
  val DOC_COMMA: SyntaxElementType = SyntaxElementType("DOC_COMMA")

  val DOC_COMMENT_BAD_CHARACTER: SyntaxElementType = SyntaxElementType("DOC_COMMENT_BAD_CHARACTER")
}