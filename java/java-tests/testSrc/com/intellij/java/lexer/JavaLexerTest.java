// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lexer;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class JavaLexerTest extends AbstractBasicJavaLexerTest {
  @Override
  protected @NotNull Lexer createLexer() {
    if (getTestName(false).endsWith("JDK21_Preview")) {
      return JavaParserDefinition.createLexer(LanguageLevel.JDK_21_PREVIEW);
    }
    return JavaParserDefinition.createLexer(LanguageLevel.HIGHEST);
  }
}