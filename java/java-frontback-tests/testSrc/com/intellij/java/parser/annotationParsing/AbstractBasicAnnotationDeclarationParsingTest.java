// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.annotationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicAnnotationDeclarationParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicAnnotationDeclarationParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/annotationParsing/declaration", configurator);
  }

  @Test
  public void testSimple() { doTest(true); }

  @Test
  public void testDefault() { doTest(true); }

  @Test
  public void testNested() { doTest(true); }

  @Test
  public void testInner() { doTest(true); }

  @Test
  public void testOtherMembers() { doTest(true); }
}
