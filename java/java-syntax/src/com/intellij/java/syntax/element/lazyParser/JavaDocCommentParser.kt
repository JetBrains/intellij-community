// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element.lazyParser

import com.intellij.java.syntax.element.JavaDocSyntaxElementType
import com.intellij.java.syntax.lexer.JavaDocLexer
import com.intellij.java.syntax.parser.JavaDocParser
import com.intellij.platform.syntax.LazyLexingContext
import com.intellij.platform.syntax.LazyParser
import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.prepareProduction
import com.intellij.pom.java.LanguageLevel

internal class JavaDocCommentParser : LazyParser {
  override fun parse(parsingContext: LazyParsingContext): ProductionResult {
    val syntaxTreeBuilder = parsingContext.syntaxTreeBuilder
    parseFragment(syntaxTreeBuilder, JavaDocSyntaxElementType.DOC_COMMENT, false) {
      JavaDocParser(syntaxTreeBuilder, languageLevel).parseDocCommentText()
    }
    return prepareProduction(syntaxTreeBuilder)
  }

  override fun canBeReparsedIncrementally(parsingContext: LazyParsingContext): Boolean {
    val newText = parsingContext.text
    val tokenList = parsingContext.tokenList

    return tokenList.tokenCount == 1 &&
           tokenList.getTokenType(0) == JavaDocSyntaxElementType.DOC_COMMENT &&
           newText.startsWith("/**") &&
           newText.endsWith("*/")
  }

  override fun createLexer(lexingContext: LazyLexingContext): Lexer =
    JavaDocLexer(languageLevel)

  private val languageLevel get() = LanguageLevel.HIGHEST
}