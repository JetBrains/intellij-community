// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestConfigurator;
import com.intellij.lang.java.parser.BasicReferenceParser;
import com.intellij.lang.java.parser.JavaParser;

public class ReferenceParserTest extends AbstractBasicReferenceParserTest {
  public ReferenceParserTest() {
    super(new JavaParsingTestConfigurator());
  }

  @Override
  protected void doRefParserTest(String text, boolean incomplete) {
    doParserTest(text, builder -> JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, incomplete, false, false, false));
  }

  @Override
  protected void doTypeParserTest(String text) {
    int flags = BasicReferenceParser.ELLIPSIS | BasicReferenceParser.DIAMONDS | BasicReferenceParser.DISJUNCTIONS;
    doParserTest(text, builder -> JavaParser.INSTANCE.getReferenceParser().parseType(builder, flags));
  }

  @Override
  protected void doTypeParamsParserTest(String text) {
    doParserTest(text, builder -> JavaParser.INSTANCE.getReferenceParser().parseTypeParameters(builder));
  }
}