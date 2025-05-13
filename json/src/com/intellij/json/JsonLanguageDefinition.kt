// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.syntax.JsonSyntaxLexer
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.psi.LanguageSyntaxDefinition
import com.intellij.platform.syntax.util.runtime.SyntaxGeneratedParserRuntime
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.syntaxElementTypeSetOf

class JsonLanguageDefinition : LanguageSyntaxDefinition {
  override fun getLexer(): Lexer {
    return JsonSyntaxLexer()
  }

  override fun getCommentTokens(): SyntaxElementTypeSet = syntaxElementTypeSetOf(JsonSyntaxElementTypes.LINE_COMMENT,
                                                                                 JsonSyntaxElementTypes.BLOCK_COMMENT)

  override fun getPairedBraces(): Collection<SyntaxGeneratedParserRuntime.BracePair> = listOf(
    SyntaxGeneratedParserRuntime.BracePair(JsonSyntaxElementTypes.L_CURLY, JsonSyntaxElementTypes.R_CURLY, true),
    SyntaxGeneratedParserRuntime.BracePair(JsonSyntaxElementTypes.L_BRACKET, JsonSyntaxElementTypes.R_BRACKET, true),
  )
}