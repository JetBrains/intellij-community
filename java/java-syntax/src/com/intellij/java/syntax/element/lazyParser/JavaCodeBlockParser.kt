// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element.lazyParser

import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.getLanguageLevel
import com.intellij.java.syntax.parser.JavaParser
import com.intellij.platform.syntax.LazyParser
import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.prepareProduction
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil

internal class JavaCodeBlockParser : LazyParser {
  override fun parse(parsingContext: LazyParsingContext): ProductionResult {
    val level = getLanguageLevel(parsingContext)
    val javaParser = JavaParser(languageLevel = level)
    val builder = parsingContext.syntaxTreeBuilder
    javaParser.statementParser.parseCodeBlockDeep(builder, true)
    return prepareProduction(builder)
  }

  override fun canBeReparsedIncrementally(parsingContext: LazyParsingContext): Boolean {
    return SyntaxBuilderUtil.hasProperBraceBalance(
      tokenList = parsingContext.tokenList,
      leftBrace = JavaSyntaxTokenType.LBRACE,
      rightBrace = JavaSyntaxTokenType.RBRACE,
      cancellationProvider = parsingContext.cancellationProvider,
    )
  }
}