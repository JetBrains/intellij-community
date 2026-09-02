// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet

import com.intellij.lexer.Lexer
import com.intellij.psi.search.scope.packageSet.lexer.ScopeTokenTypes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProjectPathPackageSetParserExtension : FilePackageSetParserExtension() {
  override fun parseScope(lexer: Lexer): String? {
    if (lexer.tokenType != ScopeTokenTypes.IDENTIFIER) return null
    if (ProjectPathPatternPackageSet.SCOPE_PROJECT_PATH != lexer.tokenText()) return null

    val end = lexer.tokenEnd
    if (end >= lexer.bufferEnd || lexer.bufferSequence[end] != ':') return null
    lexer.advance()
    return ProjectPathPatternPackageSet.SCOPE_PROJECT_PATH
  }

  override fun parsePackageSet(lexer: Lexer, scope: String, modulePattern: String?): PackageSet? {
    if (ProjectPathPatternPackageSet.SCOPE_PROJECT_PATH != scope) return null
    if (modulePattern != null) return null
    return ProjectPathPatternPackageSet(parseFilePattern(lexer))
  }

  private fun Lexer.tokenText(): String = bufferSequence.subSequence(tokenStart, tokenEnd).toString()
}
