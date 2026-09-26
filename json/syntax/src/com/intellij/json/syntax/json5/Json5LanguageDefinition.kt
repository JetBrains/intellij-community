// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax.json5

import com.intellij.json.syntax.JsonLanguageDefinition
import com.intellij.platform.syntax.lexer.Lexer

class Json5LanguageDefinition : JsonLanguageDefinition() {
  override fun createLexer(): Lexer {
    return Json5SyntaxLexer()
  }
}