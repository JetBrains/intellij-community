// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import org.jetbrains.annotations.PropertyKey
import kotlin.jvm.JvmStatic

class ExpressionParser(
  private val myNewExpressionParser: PrattExpressionParser,
) {
  fun parse(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    return myNewExpressionParser.parse(builder)
  }

  fun parseArgumentList(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    return myNewExpressionParser.parseArgumentList(builder)
  }

  fun parseArrayInitializer(
    builder: SyntaxTreeBuilder,
    type: SyntaxElementType,
    elementParser: (SyntaxTreeBuilder) -> SyntaxTreeBuilder.Marker?,
    missingElementKey: @PropertyKey(resourceBundle = JavaSyntaxBundle.BUNDLE) String,
  ): SyntaxTreeBuilder.Marker {
    return myNewExpressionParser.parseArrayInitializer(builder, type, elementParser, missingElementKey)
  }

  fun parseConditional(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    return myNewExpressionParser.tryParseWithPrecedenceAtMost(builder, CONDITIONAL_EXPR_PRECEDENCE, FORBID_LAMBDA_MASK)
  }

  fun parseAssignmentForbiddingLambda(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    return myNewExpressionParser.parse(builder, FORBID_LAMBDA_MASK)
  }

  companion object {
    @JvmStatic
    val SHIFT_OPS: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.LTLT, JavaSyntaxTokenType.GTGT, JavaSyntaxTokenType.GTGTGT)

    @JvmStatic
    val ADDITIVE_OPS: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.PLUS, JavaSyntaxTokenType.MINUS)

    @JvmStatic
    val MULTIPLICATIVE_OPS: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.ASTERISK, JavaSyntaxTokenType.DIV, JavaSyntaxTokenType.PERC)

    @JvmStatic
    val ASSIGNMENT_OPS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
      JavaSyntaxTokenType.EQ, JavaSyntaxTokenType.ASTERISKEQ, JavaSyntaxTokenType.DIVEQ, JavaSyntaxTokenType.PERCEQ,
      JavaSyntaxTokenType.PLUSEQ, JavaSyntaxTokenType.MINUSEQ,
      JavaSyntaxTokenType.LTLTEQ, JavaSyntaxTokenType.GTGTEQ, JavaSyntaxTokenType.GTGTGTEQ, JavaSyntaxTokenType.ANDEQ,
      JavaSyntaxTokenType.OREQ, JavaSyntaxTokenType.XOREQ)

    const val FORBID_LAMBDA_MASK: Int = 0x1
  }
}
