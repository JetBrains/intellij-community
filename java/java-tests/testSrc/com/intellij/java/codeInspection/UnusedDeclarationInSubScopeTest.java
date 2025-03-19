// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class UnusedDeclarationInSubScopeTest extends AbstractUnusedDeclarationTest {

  @NotNull
  @Override
  protected AnalysisScope createAnalysisScope(VirtualFile sourceDir) {
    AnalysisScope scope = super.createAnalysisScope(sourceDir);
    scope.setIncludeTestSource(false);
    return scope;
  }

  public void testParameterUsedInOutOfScopeOverrider() {
    doTest();
  }

  public void testMethodUsedOutOfScopeButParameterIsUnused() {
    doTest();
  }

  public void testConstructorUsedInOutOfScopeJava() {
    doTest();
  }

  public void testExternallyUsedClass() {
    doTest();
  }

  @Override
  protected void doTest() {
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }
}