// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JsonSyntaxParserUtil")

package com.intellij.json.syntax

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.prepareProduction
import com.intellij.platform.syntax.util.log.logger
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.isBalancedBlock
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.parseBlockLazy
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime
import com.intellij.platform.syntax.util.runtime.SyntaxRuntimeBundle
import kotlin.jvm.JvmName

fun consumeArrayContentIfTooDeep(runtime: SyntaxGeneratedParserRuntime, level: Int): Boolean =
  consumeContentIfTooDeep(runtime, level, "array", JsonSyntaxElementTypes.R_BRACKET)

fun consumeObjectContentIfTooDeep(runtime: SyntaxGeneratedParserRuntime, level: Int): Boolean =
  consumeContentIfTooDeep(runtime, level, "object", JsonSyntaxElementTypes.R_CURLY)

/** if the current tree structure is too deep, consume the content in a flat way with an error */
@Suppress("SameReturnValue")
private fun consumeContentIfTooDeep(
  runtime: SyntaxGeneratedParserRuntime,
  level: Int,
  name: String,
  closingToken: SyntaxElementType,
): Boolean {
  if (level + 1 < runtime.maxRecursionDepth) {
    return true
  }

  val builder = runtime.syntaxBuilder
  builder.error(SyntaxRuntimeBundle.message("parsing.error.maximum.recursion.level.reached.in", runtime.maxRecursionDepth, name))

  while (builder.tokenType != closingToken && builder.tokenType != null) {
    builder.advanceLexer()
  }

  return true
}

internal fun leftoverErrorInObject(runtime: SyntaxGeneratedParserRuntime, level: Int): Boolean {
  val b = runtime.syntaxBuilder
  val currentToken = requireNotNull(b.tokenType) { "this function must not be called at EOF" }
  b.error(SyntaxRuntimeBundle.message("parsing.error.leftover.in.object", currentToken))
  return true
}

fun shallowParseObject(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
  return s.syntaxBuilder.parseBlockLazy(
    leftBrace = JsonSyntaxElementTypes.L_CURLY,
    rightBrace = JsonSyntaxElementTypes.R_CURLY,
    codeBlock = JsonSyntaxElementTypes.OBJECT
  ) != null
}

fun isObjectReparseable(tokenList: TokenList, cancellationProvider: CancellationProvider): Boolean {
  return isBalancedBlock(
    leftBrace = JsonSyntaxElementTypes.L_CURLY,
    rightBrace = JsonSyntaxElementTypes.R_CURLY,
    cancellationProvider = cancellationProvider,
    tokenList = tokenList,
  )
}

private const val JSON_PARSING_MAX_RECURSION_DEPTH: Int = 1000

fun parseObject(syntaxTreeBuilder: SyntaxTreeBuilder, depthLevel: Int): ProductionResult {
  if (depthLevel * 2 > JSON_PARSING_MAX_RECURSION_DEPTH) { // multiplying by 2 because parser recursion depth increases faster than the tree depth
    val marker = syntaxTreeBuilder.mark()
    syntaxTreeBuilder.advanceToEOF()
    marker.done(JsonSyntaxElementTypes.OBJECT)
  }
  else {
    val runtime = SyntaxGeneratedParserRuntime(
      syntaxBuilder = syntaxTreeBuilder,
      maxRecursionDepth = JSON_PARSING_MAX_RECURSION_DEPTH,
      isLanguageCaseSensitive = true,
      braces = syntaxDefinition.getPairedBraces(),
      logger = logger("com.intellij.json.syntax.JsonSyntaxParser"),
      parserUserState = null
    )
    JsonSyntaxParser.parse(JsonSyntaxElementTypes.OBJECT, runtime)
  }
  return prepareProduction(syntaxTreeBuilder)
}