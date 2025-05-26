// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.java.syntax.parser.JavaParser
import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.LazyParser
import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.prepareProduction
import com.intellij.platform.syntax.tree.SyntaxNode
import com.intellij.platform.syntax.util.cancellation.cancellationProvider
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil
import com.intellij.pom.java.LanguageLevel

internal class JavaCodeBlockParser : LazyParser {
  override fun parse(parsingContext: LazyParsingContext): ProductionResult {
    return doParse(
      node = parsingContext.node,
      cachedLexemes = parsingContext.tokenList,
      text = parsingContext.text,
      level = getLanguageLevel(parsingContext),
      cancellationProvider = cancellationProvider()
    )
  }

  override fun tryReparse(parsingContext: LazyParsingContext): ProductionResult? {
    val cancellationProvider = cancellationProvider()

    val level = getLanguageLevel(parsingContext)
    val tokens = cachedOrLex(
      cachedLexemes = parsingContext.tokenList,
      text = parsingContext.text,
      languageLevel = level,
      cancellationProvider = cancellationProvider
    )

    val hasProperBraceBalance = SyntaxBuilderUtil.hasProperBraceBalance(
      tokenList = tokens,
      leftBrace = JavaSyntaxTokenType.LBRACE,
      rightBrace = JavaSyntaxTokenType.RBRACE,
      cancellationProvider = cancellationProvider
    )
    if (!hasProperBraceBalance) return null

    return doParse(
      node = parsingContext.node,
      cachedLexemes = tokens,
      text = parsingContext.text,
      level = level,
      cancellationProvider = cancellationProvider
    )
  }

  private fun doParse(
    node: SyntaxNode,
    cachedLexemes: TokenList? = null,
    text: CharSequence,
    level: LanguageLevel,
    cancellationProvider: CancellationProvider?,
  ): ProductionResult {
    val builder = createSyntaxBuilder(node, text, level, cachedLexemes, cancellationProvider)
    JavaParser(level).statementParser.parseCodeBlockDeep(builder, true)
    return prepareProduction(builder)
  }
}