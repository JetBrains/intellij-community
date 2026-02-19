// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.OldJavaParsingTestConfigurator;
import com.intellij.lang.PsiBuilder;

import java.util.function.Consumer;

// used only to check the old parser
// new features are not supported
@Deprecated
public class OldExpressionParserTest extends AbstractBasicExpressionParserTest {
  public OldExpressionParserTest() {
    super(new
            OldJavaParsingTestConfigurator());
  }

  @Override
  protected void doParserTest(String text) {
    doParserTest(text,

                 (Consumer<PsiBuilder>)builder -> new com.intellij.lang.java.parser.JavaParser().getExpressionParser().parse(builder));
  }

  @Override
  protected void doParserTest() {
    doParserTest(
      (Consumer<PsiBuilder>)builder -> new com.intellij.lang.java.parser.JavaParser().getExpressionParser().parse(builder));
  }
}