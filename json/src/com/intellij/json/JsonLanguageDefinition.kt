// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.syntax.JsonSyntaxElementTypes
import com.intellij.json.syntax.JsonSyntaxLexer
import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.runtime.GrammarKitLanguageDefinition
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime

class JsonLanguageDefinition : LanguageSyntaxDefinition, GrammarKitLanguageDefinition {
  private val jsonComments = syntaxElementTypeSetOf(JsonSyntaxElementTypes.LINE_COMMENT,
                                                    JsonSyntaxElementTypes.BLOCK_COMMENT)
  private val jsonPairedBraces = listOf(
    SyntaxGeneratedParserRuntime.BracePair(JsonSyntaxElementTypes.L_CURLY, JsonSyntaxElementTypes.R_CURLY, true),
    SyntaxGeneratedParserRuntime.BracePair(JsonSyntaxElementTypes.L_BRACKET, JsonSyntaxElementTypes.R_BRACKET, true),
  )

  override fun getPairedBraces(): Collection<SyntaxGeneratedParserRuntime.BracePair> = jsonPairedBraces
  
  override fun parse(builder: SyntaxTreeBuilder) {
    throw UnsupportedOperationException("Unsupported operation - generated Parser.")
  }

  override fun createLexer(): Lexer {
    return JsonSyntaxLexer()
  }

  override val comments: SyntaxElementTypeSet
    get() = jsonComments
}