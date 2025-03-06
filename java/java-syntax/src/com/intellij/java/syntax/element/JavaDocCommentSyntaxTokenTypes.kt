// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet

/**
 * Describes JDoc comment specific to the Java Plugin (not part of the core-api)
 * @see com.intellij.lexer.JavaDocCommentTokenTypes
 */
interface JavaDocCommentSyntaxTokenTypes {
  val commentStart: SyntaxElementType
  val commentEnd: SyntaxElementType
  val commentData: SyntaxElementType
  val spaceCommentsTokenSet: SyntaxElementTypeSet
  val space: SyntaxElementType
  val tagValueToken: SyntaxElementType
  val tagValueLParen: SyntaxElementType
  val tagValueRParen: SyntaxElementType
  val tagValueSharp: SyntaxElementType
  val tagValueComma: SyntaxElementType
  val tagName: SyntaxElementType
  val tagValueLT: SyntaxElementType
  val tagValueGT: SyntaxElementType
  val inlineTagStart: SyntaxElementType
  val inlineTagEnd: SyntaxElementType
  val badCharacter: SyntaxElementType
  val commentLeadingAsterisks: SyntaxElementType

  val tagValueQuote: SyntaxElementType
    get() = commentData

  val tagValueColon: SyntaxElementType
    get() = commentData

  val codeFence: SyntaxElementType
    get() = commentData

  val rightBracket: SyntaxElementType
    get() = commentData

  val leftBracket: SyntaxElementType
    get() = commentData

  val leftParenthesis: SyntaxElementType
    get() = commentData

  val rightParenthesis: SyntaxElementType
    get() = commentData

  val sharp: SyntaxElementType
    get() = commentData

  val inlineCodeFence: SyntaxElementType
    get() = commentData

  val comma: SyntaxElementType
    get() = commentData
}
