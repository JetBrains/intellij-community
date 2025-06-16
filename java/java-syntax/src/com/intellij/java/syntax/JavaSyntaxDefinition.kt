// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax

import com.intellij.java.syntax.element.JavaWhitespaceOrCommentBindingPolicy
import com.intellij.java.syntax.element.JavaDocSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.lexer.JavaDocLexer
import com.intellij.java.syntax.lexer.JavaLexer
import com.intellij.java.syntax.lexer.JavaTypeEscapeLexer
import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.SyntaxLanguage
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.WhitespaceOrCommentBindingPolicy
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.pom.java.LanguageLevel
import kotlin.jvm.JvmStatic

object JavaSyntaxDefinition : LanguageSyntaxDefinition {
  val language: SyntaxLanguage = SyntaxLanguage("com.intellij.java")

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

  val whitespaces: SyntaxElementTypeSet = syntaxElementTypeSetOf(SyntaxTokenTypes.WHITE_SPACE)

  override fun getLexer(): Lexer = JavaLexer(LanguageLevel.HIGHEST)

  override fun getCommentTokens(): SyntaxElementTypeSet = commentSet

  override fun getWhitespaceTokens(): SyntaxElementTypeSet = whitespaces

  override fun getWhitespaceOrCommentBindingPolicy(): WhitespaceOrCommentBindingPolicy = JavaWhitespaceOrCommentBindingPolicy
}
