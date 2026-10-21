// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicImportParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicImportParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/importParsing", configurator);
  }

  @Test
  public void testUnclosed0() { doTest(true); }
  @Test
  public void testUnclosed1() { doTest(true); }
  @Test
  public void testUnclosed2() { doTest(true); }
  
  @Test
  public void testStaticImport() { doTest(true); }
  @Test
  public void testStaticImport1() { doTest(true); }

  @Test
  public void testModuleImport() { doTest(true); }
  @Test
  public void testImportWithModulePackage() { doTest(true); }
  @Test
  public void testImportWithModuleClass() { doTest(true); }
  @Test
  public void testImportModuleBrokenStatement() { doTest(true); }
}