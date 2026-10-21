// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicCommonJavaParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicCommonJavaParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/commonParsing", configurator);
  }

  @Test
  public void testSCR5202() { doTest(true); }
  @Test
  public void testIncompleteCodeBlock() { doTest(true); }
  @Test
  public void testImportListBug() { doTest(true); }
  @Test
  public void testRefParamsAfterError() { doTest(true); }
  @Test
  public void testUnclosedComment() { doTest(true); }
  @Test
  public void testIncompleteFor() { doTest(true); }
  @Test
  public void testVarPackage() { doTest(true); }
}