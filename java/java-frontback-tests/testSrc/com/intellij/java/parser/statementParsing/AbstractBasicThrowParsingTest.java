// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.statementParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicThrowParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicThrowParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/statementParsing/throw", configurator);
  }

  @Test
  public void testNormal() { doTest(true); }

  @Test
  public void testUncomplete1() { doTest(true); }
  @Test
  public void testUncomplete2() { doTest(true); }
}