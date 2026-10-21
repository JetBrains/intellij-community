// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.declarationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicFieldParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicFieldParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/declarationParsing/field", configurator);
  }

  @Test
  public void testSimple() { doTest(true); }

  @Test
  public void testMulti() { doTest(true); }

  @Test
  public void testUnclosedBracket() { doTest(true); }

  @Test
  public void testMissingInitializer() { doTest(true); }

  @Test
  public void testUnclosedComma() { doTest(true); }

  @Test
  public void testUnclosedSemicolon() { doTest(true); }

  @Test
  public void testMissingInitializerExpression() { doTest(true); }

  @Test
  public void testMultiLineUnclosed0() { doTest(true); }

  @Test
  public void testMultiLineUnclosed1() { doTest(true); }

  @Test
  public void testComplexInitializer() { doTest(true); }

  @Test
  public void testErrors() { doTest(true); }
}