// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.statementParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicAssertParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicAssertParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/statementParsing/assert", configurator);
  }

  public void testNormal1() {
    doTest(true); }
  public void testNormal2() { doTest(true); }
}