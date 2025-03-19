// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestConfigurator;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.PatternParser;

public class PatternParserTest extends AbstractBasicPatternParserTest {
  public PatternParserTest() {
    super(new JavaParsingTestConfigurator());
  }

  @Override
  protected void doParserTest(String text) {
    doParserTest(text, builder -> {
      PatternParser parser = JavaParser.INSTANCE.getPatternParser();
      if (!parser.isPattern(builder)) {
        throw new IllegalArgumentException("Pattern is not expected");
      }
      parser.parsePattern(builder);
    });
  }
}
