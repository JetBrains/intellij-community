// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lexer;

import com.intellij.java.syntax.JavaSyntaxDefinition;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class JavaLexerTest extends AbstractBasicJavaLexerTest {
  @Override
  protected @NotNull Lexer createLexer() {
    return JavaSyntaxDefinition.createLexer(LanguageLevel.HIGHEST);
  }
}