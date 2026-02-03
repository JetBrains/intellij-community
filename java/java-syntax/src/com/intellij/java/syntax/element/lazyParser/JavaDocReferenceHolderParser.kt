// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element.lazyParser

import com.intellij.java.syntax.element.JavaDocSyntaxElementType
import com.intellij.java.syntax.lexer.JavaLexer
import com.intellij.java.syntax.parser.JavaDocParser
import com.intellij.java.syntax.parser.JavaParser
import com.intellij.platform.syntax.LazyLexingContext
import com.intellij.platform.syntax.LazyParser
import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.prepareProduction
import com.intellij.pom.java.LanguageLevel

class JavaDocReferenceHolderParser : LazyParser {
  override fun parse(parsingContext: LazyParsingContext): ProductionResult {
    val syntaxTreeBuilder = parsingContext.syntaxTreeBuilder

    parseFragment(syntaxTreeBuilder, JavaDocSyntaxElementType.DOC_REFERENCE_HOLDER, false) {
      JavaDocParser(syntaxTreeBuilder, languageLevel).parseJavadocReference(JavaParser(languageLevel))
    }

    return prepareProduction(syntaxTreeBuilder)
  }

  override fun createLexer(lexingContext: LazyLexingContext): Lexer =
    JavaLexer(languageLevel)

  private val languageLevel get() = LanguageLevel.JDK_1_3
}