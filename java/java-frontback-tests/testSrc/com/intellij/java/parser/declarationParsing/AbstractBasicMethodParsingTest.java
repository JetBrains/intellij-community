// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.declarationParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicMethodParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicMethodParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/declarationParsing/method", configurator);
  }

  public void testNormal1() { doTest(true); }

  public void testNormal2() { doTest(true); }

  public void testUnclosed1() { doTest(true); }

  public void testUnclosed2() { doTest(true); }

  public void testUnclosed3() { doTest(true); }

  public void testUnclosed4() { doTest(true); }

  public void testUnclosed5() { doTest(true); }

  public void testUnclosed6() { doTest(true); }

  public void testGenericMethod() { doTest(true); }

  public void testGenericMethodErrors() { doTest(true); }

  public void testErrors0() { doTest(true); }

  public void testErrors1() { doTest(true); }

  public void testErrors2() { doTest(true); }

  public void testErrors3() { doTest(true); }

  public void testCompletionHack() { doTest(true); }

  public void testCompletionHack1() { doTest(true); }

  public void testNoLocalMethod() { doTest(true); }

  public void testWildcardParsing() { doTest(true); }
}