// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class UnusedMethodParameterTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection");


  private void doTest() {
    UnusedDeclarationInspection declarationInspection = new UnusedDeclarationInspection();
    declarationInspection.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    javaInspection.get().doTest("unusedMethodParameter/" + javaInspection.get().getTestName(true), declarationInspection);
  }

  @Test
  public void testFieldInAnonymousClass() {
    doTest();
  }

  @Test
  public void testUnusedParameter() {
    doTest();
  }

  @Test
  public void testUnusedConstructorParameter() {
    doTest();
  }

  @Test
  public void testRecordConstructorParameter() {
    doTest();
  }

  @Test
  public void testUsedForReading() {
    doTest();
  }

  @Test
  public void testSuppressedParameter() {
    doTest();
  }

  @Test
  public void testIgnoredParameter() {
    doTest();
  }

  @Test
  public void testMethodParametersOperatorAssignment() {
    doTest();
  }

  @Test
  public void testParameterUsedInResourceExpression() {
    doTest();
  }

  @Test
  public void testEntryPointUnusedParameter() {
    javaInspection.get().doTest("unusedMethodParameter/" + javaInspection.get().getTestName(true), new UnusedDeclarationInspection(), true, true);
  }

  @Test
  public void testAppMainUnusedParams() {
    doTest();
  }
}
