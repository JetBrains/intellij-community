// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.syntaxElementTypeSetOf

/**
 * @see com.intellij.lexer.JavaDocTokenTypes
 */
object JavaDocSyntaxTokenTypes {
  val spaceCommentsTokenSet: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaDocSyntaxTokenType.DOC_SPACE, JavaDocSyntaxTokenType.DOC_COMMENT_DATA)

  val allJavaDocTypes: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    JavaDocSyntaxTokenType.DOC_COMMENT_START, JavaDocSyntaxTokenType.DOC_COMMENT_END, JavaDocSyntaxTokenType.DOC_COMMENT_DATA, JavaDocSyntaxTokenType.DOC_SPACE, JavaDocSyntaxTokenType.DOC_COMMENT_LEADING_ASTERISKS, JavaDocSyntaxTokenType.DOC_TAG_NAME,
    JavaDocSyntaxTokenType.DOC_INLINE_TAG_START, JavaDocSyntaxTokenType.DOC_INLINE_TAG_END, JavaDocSyntaxTokenType.DOC_TAG_VALUE_TOKEN, JavaDocSyntaxTokenType.DOC_TAG_VALUE_DOT, JavaDocSyntaxTokenType.DOC_TAG_VALUE_COMMA,
    JavaDocSyntaxTokenType.DOC_TAG_VALUE_LPAREN, JavaDocSyntaxTokenType.DOC_TAG_VALUE_RPAREN, JavaDocSyntaxTokenType.DOC_TAG_VALUE_SHARP_TOKEN, JavaDocSyntaxTokenType.DOC_TAG_VALUE_SLASH, JavaDocSyntaxTokenType.DOC_TAG_VALUE_QUOTE, JavaDocSyntaxTokenType.DOC_TAG_VALUE_COLON,
    JavaDocSyntaxTokenType.DOC_TAG_ATTRIBUTE_NAME, JavaDocSyntaxTokenType.DOC_TAG_ATTRIBUTE_VALUE, JavaDocSyntaxTokenType.DOC_CODE_FENCE, JavaDocSyntaxTokenType.DOC_RBRACKET, JavaDocSyntaxTokenType.DOC_LBRACKET, JavaDocSyntaxTokenType.DOC_LPAREN, JavaDocSyntaxTokenType.DOC_RPAREN,
    JavaDocSyntaxTokenType.DOC_SHARP, JavaDocSyntaxTokenType.DOC_INLINE_CODE_FENCE, JavaDocSyntaxTokenType.DOC_COMMA
  )
}
