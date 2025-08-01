// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax

import com.intellij.java.syntax.element.JavaWhitespaceOrCommentBindingPolicy
import com.intellij.java.syntax.element.JavaDocSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.lexer.JavaDocLexer
import com.intellij.java.syntax.lexer.JavaLexer
import com.intellij.java.syntax.lexer.JavaTypeEscapeLexer
import com.intellij.java.syntax.parser.JavaParser
import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.SyntaxLanguage
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
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

  @JvmStatic
  fun createParser(languageLevel: LanguageLevel): JavaParser = JavaParser(languageLevel)

  override fun createLexer(): Lexer = JavaLexer(LanguageLevel.HIGHEST)

  override fun parse(builder: SyntaxTreeBuilder) {
    val root = builder.mark()
    createParser(LanguageLevel.HIGHEST).fileParser.parse(builder)
    root.done(JavaSyntaxElementType.JAVA_FILE)
  }

  override val comments: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    JavaSyntaxTokenType.END_OF_LINE_COMMENT,
    JavaSyntaxTokenType.C_STYLE_COMMENT,
    JavaDocSyntaxElementType.DOC_COMMENT
  )

  override val whitespaces: SyntaxElementTypeSet = syntaxElementTypeSetOf(SyntaxTokenTypes.WHITE_SPACE)

  override val whitespaceOrCommentBindingPolicy: WhitespaceOrCommentBindingPolicy = JavaWhitespaceOrCommentBindingPolicy
}
