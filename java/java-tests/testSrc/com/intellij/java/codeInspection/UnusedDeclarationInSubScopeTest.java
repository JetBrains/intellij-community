// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.UnusedDeclarationFixtureKt.unusedDeclarationFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class UnusedDeclarationInSubScopeTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<UnusedDeclarationFixture> unusedDeclaration =
    unusedDeclarationFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection", (project, sourceDir) -> {
      AnalysisScope scope = JavaInspectionFixture.directoryScope(project, sourceDir);
      scope.setIncludeTestSource(false);
      return scope;
    });

  @Test
  public void testParameterUsedInOutOfScopeOverrider() {
    doTest();
  }

  @Test
  public void testMethodUsedOutOfScopeButParameterIsUnused() {
    doTest();
  }

  @Test
  public void testConstructorUsedInOutOfScopeJava() {
    doTest();
  }

  @Test
  public void testExternallyUsedClass() {
    doTest();
  }

  private void doTest() {
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }
}