// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.java.syntax.JavaSyntaxBundle.message
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.WhiteSpaceAndCommentSetHolder
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.expect
import com.intellij.platform.syntax.util.parser.SyntaxTreeBuilderAdapter
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Experimental
object JavaParserUtil {
  @Suppress("unused")
  fun setParseStatementCodeBlocksDeep(builder: SyntaxTreeBuilder, deep: Boolean) {
    // todo implement me please when necessary
    //      most likely, the state should be stored in JavaParser instead, not in SyntaxTreeBuilder
  }

  @Suppress("unused")
  fun isParseStatementCodeBlocksDeep(builder: SyntaxTreeBuilder): Boolean {
    return false
  }

  fun done(
    marker: SyntaxTreeBuilder.Marker,
    type: SyntaxElementType,
    languageLevel: LanguageLevel,
    commentSetHolder: WhiteSpaceAndCommentSetHolder,
  ) {
    marker.done(type)
    val left =
      if (commentSetHolder.precedingCommentSet.contains(type))
        commentSetHolder.getPrecedingCommentBinder(languageLevel)
      else
        null

    val right =
      if (commentSetHolder.trailingCommentSet.contains(type))
        commentSetHolder.trailingCommentBinder
      else
        null

    marker.setCustomEdgeTokenBinders(left, right)
  }

  fun exprType(marker: SyntaxTreeBuilder.Marker?): SyntaxElementType? {
    return marker?.getNodeType()
  }

  // used instead of SyntaxTreeBuilder.error() as it keeps all subsequent error messages
  fun error(builder: SyntaxTreeBuilder, message: @NlsContexts.ParsingError String) {
    builder.mark().error(message)
  }

  fun error(
    builder: SyntaxTreeBuilder,
    message: @NlsContexts.ParsingError String,
    before: SyntaxTreeBuilder.Marker?,
  ) {
    if (before == null) {
      error(builder, message)
    }
    else {
      before.precede().errorBefore(message, before)
    }
  }

  fun expectOrError(
    builder: SyntaxTreeBuilder,
    expected: SyntaxElementTypeSet,
    key: @PropertyKey(resourceBundle = JavaSyntaxBundle.BUNDLE) String,
  ): Boolean {
    if (!builder.expect(expected)) {
      error(builder, message(key))
      return false
    }
    return true
  }

  fun expectOrError(
    builder: SyntaxTreeBuilder,
    expected: SyntaxElementType?,
    key: @PropertyKey(resourceBundle = JavaSyntaxBundle.BUNDLE) String,
  ): Boolean {
    if (!builder.expect(expected)) {
      error(builder, message(key))
      return false
    }
    return true
  }

  fun emptyElement(builder: SyntaxTreeBuilder, type: SyntaxElementType) {
    builder.mark().done(type)
  }

  fun emptyElement(before: SyntaxTreeBuilder.Marker, type: SyntaxElementType) {
    before.precede().doneBefore(type, before)
  }

  fun semicolon(builder: SyntaxTreeBuilder) {
    expectOrError(builder, JavaSyntaxTokenType.SEMICOLON, "expected.semicolon")
  }

  fun braceMatchingBuilder(builder: SyntaxTreeBuilder): SyntaxTreeBuilder {
    val pos = builder.mark()

    var braceCount = 1
    while (!builder.eof()) {
      val tokenType = builder.tokenType
      if (tokenType === JavaSyntaxTokenType.LBRACE) {
        braceCount++
      }
      else if (tokenType === JavaSyntaxTokenType.RBRACE) braceCount--
      if (braceCount == 0) break
      builder.advanceLexer()
    }
    val stopAt = builder.currentOffset

    pos.rollbackTo()

    return stoppingBuilder(builder, stopAt)
  }

  fun stoppingBuilder(builder: SyntaxTreeBuilder, stopAt: Int): SyntaxTreeBuilder {
    return object : SyntaxTreeBuilderAdapter(builder) {
      override val tokenType: SyntaxElementType?
        get() = if (currentOffset < stopAt) super.tokenType else null

      override fun eof(): Boolean {
        return currentOffset >= stopAt || super.eof()
      }
    }
  }
}