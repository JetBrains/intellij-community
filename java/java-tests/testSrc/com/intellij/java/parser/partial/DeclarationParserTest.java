// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestConfigurator;
import com.intellij.java.syntax.parser.JavaParser;

public class DeclarationParserTest extends AbstractBasicDeclarationParserTest {

  public DeclarationParserTest() {
    super(new JavaParsingTestConfigurator());
  }

  @Override
  protected void doParserTest(String text, boolean isAnnotation, boolean isEnum) {
    doParserTest(text, (builder, languageLevel) -> new JavaParser(languageLevel).getDeclarationParser().parseClassBodyWithBraces(builder, isAnnotation, isEnum));
  }
}