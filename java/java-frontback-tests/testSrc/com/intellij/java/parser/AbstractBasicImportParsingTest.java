// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicImportParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicImportParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/importParsing", configurator);
  }

  public void testUnclosed0() { doTest(true); }
  public void testUnclosed1() { doTest(true); }
  public void testUnclosed2() { doTest(true); }
  
  public void testStaticImport() { doTest(true); }
  public void testStaticImport1() { doTest(true); }

  public void testModuleImport() { doTest(true); }
  public void testImportWithModulePackage() { doTest(true); }
  public void testImportWithModuleClass() { doTest(true); }
  public void testImportModuleBrokenStatement() { doTest(true); }
}