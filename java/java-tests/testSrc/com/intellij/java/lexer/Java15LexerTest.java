// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lexer;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LexerTestCase;

public class Java15LexerTest extends LexerTestCase {
  public void testNonSealed() {
    doTest("non-sealed", "NON_SEALED ('non-sealed')");
  }

  @Override
  protected Lexer createLexer() {
    return JavaParserDefinition.createLexer(LanguageLevel.JDK_15_PREVIEW);
  }

  @Override
  protected String getDirPath() {
    return "";
  }
}