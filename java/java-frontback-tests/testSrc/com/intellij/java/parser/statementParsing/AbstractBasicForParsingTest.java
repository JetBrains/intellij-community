// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.statementParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicForParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicForParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/statementParsing/for", configurator);
  }

  public void testNormal1() { doTest(true); }
  public void testNormal2() { doTest(true); }
  public void testForEach1() { doTest(true); }

  public void testUncomplete1() { doTest(true); }
  public void testUncomplete2() { doTest(true); }
  public void testUncomplete3() { doTest(true); }
  public void testUncomplete4() { doTest(true); }
  public void testUncomplete5() { doTest(true); }
  public void testUncomplete6() { doTest(true); }
  public void testUncomplete7() { doTest(true); }
  public void testUncomplete8() { doTest(true); }
  public void testUncomplete9() { doTest(true); }
  public void testUncomplete10() { doTest(true); }
}