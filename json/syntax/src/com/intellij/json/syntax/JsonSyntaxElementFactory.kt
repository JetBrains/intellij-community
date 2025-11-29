// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax

import com.intellij.platform.syntax.LazyParser
import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.tree.SyntaxNode
import kotlin.jvm.JvmStatic

internal object JsonSyntaxElementFactory {
  @JvmStatic
  fun getType(name: String): SyntaxElementType = when (name) {
    "OBJECT" -> LAZY_OBJECT
    else -> throw IllegalArgumentException(name)
  }
}

private val LAZY_OBJECT: SyntaxElementType = SyntaxElementType(debugName = "OBJECT", lazyParser = object : LazyParser {
  override fun canBeReparsedIncrementally(parsingContext: LazyParsingContext): Boolean {
    return isObjectReparseable(
      tokenList = parsingContext.tokenList,
      cancellationProvider = parsingContext.cancellationProvider
    )
  }

  override fun parse(parsingContext: LazyParsingContext): ProductionResult =
    parseObject(parsingContext.syntaxTreeBuilder, parsingContext.node.depth)
})

private val SyntaxNode.depth: Int
  get() = generateSequence(parent()) { it.parent() }.count()

