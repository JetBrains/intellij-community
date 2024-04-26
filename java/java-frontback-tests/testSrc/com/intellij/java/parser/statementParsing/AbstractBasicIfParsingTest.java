// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.statementParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicIfParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicIfParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/statementParsing/if", configurator);
  }

  public void testNormalWithElse() { doTest(true); }

  public void testNormalNoElse() { doTest(true); }

  public void testUncomplete1() { doTest(true); }

  public void testUncomplete2() { doTest(true); }

  public void testUncomplete3() { doTest(true); }

  public void testUncomplete4() { doTest(true); }

  public void testUncomplete5() { doTest(true); }

  public void testUncomplete6() { doTest(true); }

  public void testUncomplete7() { doTest(true); }
}