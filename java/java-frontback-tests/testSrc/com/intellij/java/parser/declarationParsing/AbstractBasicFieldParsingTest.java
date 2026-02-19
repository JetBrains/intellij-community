// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.declarationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicFieldParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicFieldParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/declarationParsing/field", configurator);
  }

  public void testSimple() { doTest(true); }

  public void testMulti() { doTest(true); }

  public void testUnclosedBracket() { doTest(true); }

  public void testMissingInitializer() { doTest(true); }

  public void testUnclosedComma() { doTest(true); }

  public void testUnclosedSemicolon() { doTest(true); }

  public void testMissingInitializerExpression() { doTest(true); }

  public void testMultiLineUnclosed0() { doTest(true); }

  public void testMultiLineUnclosed1() { doTest(true); }

  public void testComplexInitializer() { doTest(true); }

  public void testErrors() { doTest(true); }
}