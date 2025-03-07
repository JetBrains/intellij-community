// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax

import com.intellij.java.syntax.element.JavaDocSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.lexer.JavaDocLexer
import com.intellij.java.syntax.lexer.JavaLexer
import com.intellij.java.syntax.lexer.JavaTypeEscapeLexer
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.pom.java.LanguageLevel

object JavaSyntaxDefinition {
  @JvmStatic
  fun createLexer(languageLevel: LanguageLevel): Lexer = JavaLexer(languageLevel)

  @JvmStatic
  fun createDocLexer(languageLevel: LanguageLevel): Lexer = JavaDocLexer(languageLevel)

  @JvmStatic
  fun createLexerWithMarkdownEscape(level: LanguageLevel): Lexer = JavaTypeEscapeLexer(createLexer(level))

  val commentSet: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    JavaSyntaxTokenType.END_OF_LINE_COMMENT,
    JavaSyntaxTokenType.C_STYLE_COMMENT,
    JavaDocSyntaxElementType.DOC_COMMENT
  )
}
