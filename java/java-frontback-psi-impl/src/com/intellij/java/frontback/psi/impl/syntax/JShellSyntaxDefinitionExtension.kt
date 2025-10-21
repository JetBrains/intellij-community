// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.frontback.psi.impl.syntax

import com.intellij.java.syntax.JavaSyntaxDefinition
import com.intellij.java.syntax.parser.JShellParser
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.pom.java.LanguageLevel

internal class JShellSyntaxDefinitionExtension : LanguageSyntaxDefinition {

  fun createParser(languageLevel: LanguageLevel): JShellParser = JShellParser(languageLevel)

  override fun parse(builder: SyntaxTreeBuilder) {
    createParser(LanguageLevel.HIGHEST).parse(builder)
  }

  override fun createLexer(): Lexer = JavaSyntaxDefinition.createLexer(LanguageLevel.HIGHEST)

  override val comments = JavaSyntaxDefinition.comments
}