// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.util.AccessModifier;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class SameParameterValueLocalTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/sameParameterValue/");

  private final SameParameterValueInspection myGlobalTool = new SameParameterValueInspection();
  private final LocalInspectionTool myTool = myGlobalTool.getSharedLocalInspectionTool();

  private void doTest() {
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool);
  }
  
  @Test
  public void testEntryPoint() { doTest(); }
  @Test
  public void testNotReportedDueToHighVisibility() { doTest(); }
  @Test
  public void testNativeMethod() { doTest(); }
  @Test
  public void testNegativeDouble() { doTest(); }
  @Test
  public void testMethodReferenceInCallArguments() { doTest(); }
  @Test
  public void testFixAvailable() { doTest(); }
  @Test
  public void testFixNotAvailable() { doTest(); }
  @Test
  public void testOverrideGroovy() { doTest(); }
  @Test
  public void testMethodReferences() { doTest(); }
  @Test
  public void testLocalClassArgument() { doTest(); }
  @Test
  public void testWrittenVar() { doTest(); }
  @Test
  public void testVarargs() { doTest(); }

  @Test
  public void testMethodWithSuper() {
    myGlobalTool.highestModifier = AccessModifier.PUBLIC;
    doTest();
  }

  @Test
  public void testFixNotAvailableIsShown() {
    myGlobalTool.ignoreWhenRefactoringIsComplicated = false;
    doTest();
  }

  @Test
  public void testUsageCount() {
    myGlobalTool.minimalUsageCount = 5;
    doTest();
  }
}
