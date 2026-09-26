// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.tree.SyntaxNode
import kotlin.jvm.JvmStatic

internal object JsonSyntaxElementFactory {
  @JvmStatic
  fun getType(name: String): SyntaxElementType = when (name) {
    "OBJECT" -> if (JsonLazyParsing) LAZY_OBJECT else OBJECT
    "ARRAY" -> if (JsonLazyParsing) LAZY_ARRAY else ARRAY
    else -> throw IllegalArgumentException(name)
  }
}

private val LAZY_OBJECT: SyntaxElementType = lazySyntaxElementType(
  name = "OBJECT",
  isReparseable = ::isObjectReparseable,
  parse = ::parseObject,
)

private val LAZY_ARRAY: SyntaxElementType = lazySyntaxElementType(
  name = "ARRAY",
  isReparseable = ::isArrayReparseable,
  parse = ::parseArray,
)

private val OBJECT = SyntaxElementType("OBJECT")
private val ARRAY = SyntaxElementType("ARRAY")

private fun lazySyntaxElementType(
  name: String,
  isReparseable: (TokenList, CancellationProvider) -> Boolean,
  parse: (SyntaxTreeBuilder, Int) -> ProductionResult,
): SyntaxElementType = SyntaxElementType(debugName = name, lazyParser = object : com.intellij.platform.syntax.LazyParser {
  override fun canBeReparsedIncrementally(parsingContext: LazyParsingContext): Boolean =
    isReparseable(parsingContext.tokenList, parsingContext.cancellationProvider)

  override fun parse(parsingContext: LazyParsingContext): ProductionResult =
    parse(parsingContext.syntaxTreeBuilder, parsingContext.node.depth)
})

private val SyntaxNode.depth: Int
  get() = generateSequence(parent()) { it.parent() }.count()
