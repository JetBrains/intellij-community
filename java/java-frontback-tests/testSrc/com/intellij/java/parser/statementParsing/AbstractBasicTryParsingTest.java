// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.statementParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicTryParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicTryParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/statementParsing/try", configurator);
  }

  @Test
  public void testNormal1() { doTest(true); }

  @Test
  public void testNormal2() { doTest(true); }

  @Test
  public void testNormal3() { doTest(true); }

  @Test
  public void testNormal4() { doTest(true); }

  @Test
  public void testIncomplete1() { doTest(true); }

  @Test
  public void testIncomplete2() { doTest(true); }

  @Test
  public void testIncomplete3() { doTest(true); }

  @Test
  public void testIncomplete4() { doTest(true); }

  @Test
  public void testIncomplete5() { doTest(true); }

  @Test
  public void testIncomplete6() { doTest(true); }

  @Test
  public void testIncomplete7() { doTest(true); }

  @Test
  public void testIncomplete8() { doTest(true); }

  @Test
  public void testIncomplete9() { doTest(true); }
}