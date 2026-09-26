// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import org.jetbrains.annotations.PropertyKey

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
    const val FORBID_LAMBDA_MASK: Int = 0x1
  }
}
