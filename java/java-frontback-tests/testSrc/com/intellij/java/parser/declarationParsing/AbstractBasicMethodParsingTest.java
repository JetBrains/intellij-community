// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.declarationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicMethodParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicMethodParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/declarationParsing/method", configurator);
  }

  @Test
  public void testNormal1() { doTest(true); }

  @Test
  public void testNormal2() { doTest(true); }

  @Test
  public void testUnclosed1() { doTest(true); }

  @Test
  public void testUnclosed2() { doTest(true); }

  @Test
  public void testUnclosed3() { doTest(true); }

  @Test
  public void testUnclosed4() { doTest(true); }

  @Test
  public void testUnclosed5() { doTest(true); }

  @Test
  public void testUnclosed6() { doTest(true); }

  @Test
  public void testGenericMethod() { doTest(true); }

  @Test
  public void testGenericMethodErrors() { doTest(true); }

  @Test
  public void testErrors0() { doTest(true); }

  @Test
  public void testErrors1() { doTest(true); }

  @Test
  public void testErrors2() { doTest(true); }

  @Test
  public void testErrors3() { doTest(true); }

  @Test
  public void testCompletionHack() { doTest(true); }

  @Test
  public void testCompletionHack1() { doTest(true); }

  @Test
  public void testNoLocalMethod() { doTest(true); }

  @Test
  public void testWildcardParsing() { doTest(true); }
}