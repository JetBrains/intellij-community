// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicCommonJavaParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicCommonJavaParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/commonParsing", configurator);
  }

  public void testSCR5202() { doTest(true); }
  public void testIncompleteCodeBlock() { doTest(true); }
  public void testImportListBug() { doTest(true); }
  public void testRefParamsAfterError() { doTest(true); }
  public void testUnclosedComment() { doTest(true); }
  public void testIncompleteFor() { doTest(true); }
  public void testVarPackage() { doTest(true); }
}