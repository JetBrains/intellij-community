// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.statementParsing;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicDeclarationsWithGenericsParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicDeclarationsWithGenericsParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/statementParsing/genericsParsing", configurator);
  }

  @Test
  public void testLocalVar() { doTest(true); }

  @Test
  public void testFor() { doTest(true); }
}