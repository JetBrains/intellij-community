// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.UnusedDeclarationFixtureKt.unusedDeclarationFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class UnusedParameterDeclarationTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<UnusedDeclarationFixture> unusedDeclaration = unusedDeclarationFixture(projectFixture);


  @Test
  public void testParameterUsedInLambda() {
    doTest();
  }

  @Test
  public void testParameterUsedInNestedLambda() {
    doTest();
  }

  @Test
  public void testOnlyParameterUsedInLambda() {
    doTest();
  }

  @Test
  public void testParameterNotUsedInLambda() {
    doTest();
  }

  @Test
  public void testParameterNotUsedInNestedLambda() {
    doTest();
  }

  @Test
  public void testParameterUsedInMethodRef() {
    doTest();
  }

  @Test
  public void testParameterUsedInAnonymousClass() {
    doTest();
  }

  @Test
  public void testParameterNotUsedInAnonymousClass() {
    doTest();
  }

  private void doTest() {
    unusedDeclaration.get().doTest("deadCode/" + unusedDeclaration.get().getTestName(true), unusedDeclaration.get().getToolWrapper());
  }
}
