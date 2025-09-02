// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.lexer

import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.util.lexer.MergingLexerAdapterBase

/**
 * Lexer to wrap around the Java Lexer when parsing Java types for the markdown JavaDoc (JEP-467)
 * In reference links, array types are escaped:  "char\[\]" which the JavaLexer doesn't like
 * <p>
 * It does this by <em>covering up</em> a <i>BAD_CHARACTER</i> token if followed by <i>[</i> or <i>]</i>
 */
internal class JavaTypeEscapeLexer(original: Lexer) : MergingLexerAdapterBase(original) {
  override fun merge(tokenType: SyntaxElementType, lexer: Lexer): SyntaxElementType {
    if (tokenType != SyntaxTokenTypes.BAD_CHARACTER) return tokenType

    val originalTokenType = original.getTokenType()
    if (originalTokenType != JavaSyntaxTokenType.LBRACKET && originalTokenType != JavaSyntaxTokenType.RBRACKET) {
      return tokenType
    }

    original.advance()
    return originalTokenType
  }
}