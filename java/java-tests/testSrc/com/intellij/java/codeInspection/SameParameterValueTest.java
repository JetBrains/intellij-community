// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.util.AccessModifier;
import com.intellij.testFramework.JavaInspectionTestCase;

public class SameParameterValueTest extends JavaInspectionTestCase {
  private SameParameterValueInspection myTool;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTool = new SameParameterValueInspection();
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/sameParameterValue/";
  }

  private void doTest() {
    doTest(getTestName(true), myTool);
  }

  public void testEntryPoint() { doTest(); }
  public void testNotReportedDueToHighVisibility() { doTest(); }
  public void testNegativeDouble() { doTest(); }
  public void testClassObject() { doTest(); }
  public void testField() { doTest(); }
  public void testOverrideGroovy() { doTest(); }
  public void testMethodReferences() { doTest(); }
  public void testLocalClassArgument() { doTest(); }

  public void testWithoutDeadCode() {
    myTool.highestModifier = AccessModifier.PUBLIC;
    doTest();
  }

  public void testVarargs() {
    myTool.ignoreWhenRefactoringIsComplicated = false;
    doTest();
  }

  public void testSimpleVararg() {
    myTool.ignoreWhenRefactoringIsComplicated = false;
    doTest();
  }
  
  public void testMethodWithSuper() {
    myTool.highestModifier = AccessModifier.PUBLIC;
    doTest();
  }

  public void testUsageCount() {
    myTool.minimalUsageCount = 5;
    doTest();
  }
}
