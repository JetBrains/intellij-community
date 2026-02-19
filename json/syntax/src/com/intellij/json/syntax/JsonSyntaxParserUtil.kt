// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JsonSyntaxParserUtil")

package com.intellij.json.syntax

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime
import com.intellij.platform.syntax.util.runtime.SyntaxRuntimeBundle
import kotlin.jvm.JvmName

fun consumeArrayContentIfTooDeep(runtime: SyntaxGeneratedParserRuntime, level: Int): Boolean =
  consumeContentIfTooDeep(runtime, level, "array", JsonSyntaxElementTypes.R_BRACKET)

fun consumeObjectContentIfTooDeep(runtime: SyntaxGeneratedParserRuntime, level: Int): Boolean =
  consumeContentIfTooDeep(runtime, level, "objet", JsonSyntaxElementTypes.R_CURLY)

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