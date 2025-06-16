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
import kotlin.jvm.JvmStatic // don't remove this import, it must be preserved for compilation in Kotlin/WasmJs

@ApiStatus.Experimental
object JavaParserUtil {
  fun done(
    marker: SyntaxTreeBuilder.Marker,
    type: SyntaxElementType,
    languageLevel: LanguageLevel,
  ) {
    marker.done(type)
    val left =
      if (WhiteSpaceAndCommentSetHolder.precedingCommentSet.contains(type))
        WhiteSpaceAndCommentSetHolder.getPrecedingCommentBinder(languageLevel)
      else
        null

    val right =
      if (WhiteSpaceAndCommentSetHolder.trailingCommentSet.contains(type))
        WhiteSpaceAndCommentSetHolder.trailingCommentBinder
      else
        null

    marker.setCustomEdgeTokenBinders(left, right)
  }

  fun exprType(marker: SyntaxTreeBuilder.Marker?): SyntaxElementType? {
    return marker?.getNodeType()
  }

  // used instead of SyntaxTreeBuilder.error() as it keeps all subsequent error messages
  @JvmStatic
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

  @JvmStatic
  fun stoppingBuilder(builder: SyntaxTreeBuilder, stopAt: Int): SyntaxTreeBuilder {
    return object : SyntaxTreeBuilderAdapter(builder) {
      override val tokenType: SyntaxElementType?
        get() = if (currentOffset < stopAt) super.tokenType else null

      override fun eof(): Boolean {
        return currentOffset >= stopAt || super.eof()
      }
    }
  }

  @JvmStatic
  fun stoppingBuilder(builder: SyntaxTreeBuilder, condition: (SyntaxElementType?, String?) -> Boolean): SyntaxTreeBuilder {
    return object : SyntaxTreeBuilderAdapter(builder) {
      override val tokenType: SyntaxElementType?
        get() = if (condition(builder.tokenType, builder.tokenText)) null else super.tokenType

      override fun eof(): Boolean =
        condition(builder.tokenType, builder.tokenText) || super.eof()
    }
  }
}