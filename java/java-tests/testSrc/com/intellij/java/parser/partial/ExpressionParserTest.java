// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestConfigurator;
import com.intellij.java.syntax.parser.JavaParser;

public class ExpressionParserTest extends AbstractBasicExpressionParserTest {
  public ExpressionParserTest() {
    super(new JavaParsingTestConfigurator());
  }

  @Override
  protected void doParserTest(String text) {
    doParserTest(text, (builder, languageLevel) -> new JavaParser(languageLevel).getExpressionParser().parse(builder));
  }

  @Override
  protected void doParserTest() {
    doParserTest((builder, languageLevel) -> new JavaParser(languageLevel).getExpressionParser().parse(builder));
  }
}