// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lexer

import com.intellij.java.syntax.JavaSyntaxDefinition
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.pom.java.LanguageLevel

class JavadocLexerTest : AbstractBasicJavadocLexerTest() {
  override fun createLexer(): Lexer {
    return JavaSyntaxDefinition.createDocLexer(LanguageLevel.HIGHEST)
  }
}