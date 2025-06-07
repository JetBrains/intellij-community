// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestConfigurator;
import com.intellij.java.syntax.parser.JavaParser;

public class StatementParserTest extends AbstractBasicStatementParserTest {
  public StatementParserTest() {
    super(new JavaParsingTestConfigurator());
  }

  @Override
  protected void doBlockParserTest(String text) {
    doParserTest(text, (builder, languageLevel) -> new JavaParser(languageLevel).getStatementParser().parseCodeBlockDeep(builder, true));
  }

  @Override
  protected void doParserTest(String text) {
    doParserTest(text, (builder, languageLevel) -> new JavaParser(languageLevel).getStatementParser().parseStatements(builder));
  }
}