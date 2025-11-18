// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.OldJavaParsingTestConfigurator;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.ReferenceParser;

import java.util.function.Consumer;

// used only to check the old parser
// new features are not supported
@Deprecated
public class OldReferenceParserTest extends AbstractBasicReferenceParserTest {
  public OldReferenceParserTest() {
    super(new OldJavaParsingTestConfigurator("test.java.file"));
  }

  @Override
  protected void doRefParserTest(String text, boolean incomplete) {
    doParserTest(text, (Consumer<PsiBuilder>)(builder) -> new JavaParser().getReferenceParser()
      .parseJavaCodeReference(builder, incomplete, false, false, false));
  }

  @Override
  protected void doTypeParserTest(String text) {
    int flags = ReferenceParser.ELLIPSIS | ReferenceParser.DIAMONDS | ReferenceParser.DISJUNCTIONS;
    doParserTest(text, (Consumer<PsiBuilder>)(builder) -> new JavaParser().getReferenceParser().parseType(builder, flags));
  }

  @Override
  protected void doTypeParamsParserTest(String text) {
    doParserTest(text, (Consumer<PsiBuilder>)(builder) -> new JavaParser().getReferenceParser().parseTypeParameters(builder));
  }
}