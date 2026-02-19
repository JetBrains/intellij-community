// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.OldJavaParsingTestConfigurator;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.PatternParser;

import java.util.function.Consumer;

// used only to check the old parser
// new features are not supported
@Deprecated
public class OldPatternParserTest extends AbstractBasicPatternParserTest {
  public OldPatternParserTest() {
    super(new OldJavaParsingTestConfigurator());
  }

  @Override
  protected void doParserTest(String text) {
    doParserTest(text, (Consumer<PsiBuilder>)(builder) -> {
      PatternParser parser = new JavaParser().getPatternParser();
      if (!parser.isPattern(builder)) {
        throw new IllegalArgumentException("Pattern is not expected");
      }
      parser.parsePattern(builder);
    });
  }
}
