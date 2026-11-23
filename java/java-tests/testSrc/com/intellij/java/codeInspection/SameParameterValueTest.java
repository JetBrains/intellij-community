// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.util.AccessModifier;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class SameParameterValueTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/sameParameterValue/");

  private SameParameterValueInspection myTool;

  @BeforeEach
  public void setUp() {
    myTool = new SameParameterValueInspection();
  }

  private void doTest() {
    javaInspection.get().doTest(javaInspection.get().getTestName(true), myTool);
  }

  @Test
  public void testEntryPoint() { doTest(); }
  @Test
  public void testNotReportedDueToHighVisibility() { doTest(); }
  @Test
  public void testNegativeDouble() { doTest(); }
  @Test
  public void testClassObject() { doTest(); }
  @Test
  public void testField() { doTest(); }
  @Test
  public void testOverrideGroovy() { doTest(); }
  @Test
  public void testMethodReferences() { doTest(); }
  @Test
  public void testLocalClassArgument() { doTest(); }
  @Test
  public void testVarargs() { doTest(); }
  @Test
  public void testSimpleVararg() { doTest(); }
  
  @Test
  public void testWithoutDeadCode() {
    myTool.highestModifier = AccessModifier.PUBLIC;
    doTest();
  }
  
  @Test
  public void testMethodWithSuper() {
    myTool.highestModifier = AccessModifier.PUBLIC;
    doTest();
  }

  @Test
  public void testUsageCount() {
    myTool.minimalUsageCount = 5;
    doTest();
  }
}
