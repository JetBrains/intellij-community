// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.syntaxElementTypeSetOf

/**
 * @see com.intellij.lexer.JavaDocTokenTypes
 */
object JavaDocSyntaxTokenTypes : JavaDocCommentSyntaxTokenTypes {
  override val commentStart: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_COMMENT_START
  override val commentEnd: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_COMMENT_END
  override val commentData: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_COMMENT_DATA
  override val spaceCommentsTokenSet: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaDocSyntaxTokenType.DOC_SPACE, JavaDocSyntaxTokenType.DOC_COMMENT_DATA)
  override val space: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_SPACE
  override val tagValueToken: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_VALUE_TOKEN
  override val tagValueLParen: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_VALUE_LPAREN
  override val tagValueRParen: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_VALUE_RPAREN
  override val tagValueQuote: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_VALUE_QUOTE
  override val tagValueColon: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_VALUE_COLON
  override val tagValueSharp: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_VALUE_SHARP_TOKEN
  override val tagValueComma: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_VALUE_COMMA
  override val tagName: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_NAME
  override val tagValueLT: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_VALUE_LT
  override val tagValueGT: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_TAG_VALUE_GT
  override val inlineTagStart: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_INLINE_TAG_START
  override val inlineTagEnd: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_INLINE_TAG_END
  override val badCharacter: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_COMMENT_BAD_CHARACTER
  override val commentLeadingAsterisks: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_COMMENT_LEADING_ASTERISKS
  override val codeFence: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_CODE_FENCE
  override val rightBracket: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_RBRACKET
  override val leftBracket: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_LBRACKET
  override val leftParenthesis: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_LPAREN
  override val rightParenthesis: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_RPAREN
  override val sharp: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_SHARP
  override val inlineCodeFence: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_INLINE_CODE_FENCE
  override val comma: SyntaxElementType get() = JavaDocSyntaxTokenType.DOC_COMMA
}
