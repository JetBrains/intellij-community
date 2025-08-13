// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.util.AccessModifier;
import com.intellij.testFramework.JavaInspectionTestCase;

public class SameParameterValueLocalTest extends JavaInspectionTestCase {
  private final SameParameterValueInspection myGlobalTool = new SameParameterValueInspection();
  private LocalInspectionTool myTool = myGlobalTool.getSharedLocalInspectionTool();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/sameParameterValue/";
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  private void doTest() {
    doTest(getTestName(true), myTool);
  }
  
  public void testEntryPoint() { doTest(); }
  public void testNotReportedDueToHighVisibility() { doTest(); }
  public void testNativeMethod() { doTest(); }
  public void testNegativeDouble() { doTest(); }
  public void testMethodReferenceInCallArguments() { doTest(); }
  public void testFixAvailable() { doTest(); }
  public void testFixNotAvailable() { doTest(); }
  public void testOverrideGroovy() { doTest(); }
  public void testMethodReferences() { doTest(); }
  public void testLocalClassArgument() { doTest(); }

  public void testMethodWithSuper() {
    myGlobalTool.highestModifier = AccessModifier.PUBLIC;
    doTest();
  }

  public void testVarargs() {
    myGlobalTool.ignoreWhenRefactoringIsComplicated = false;
    doTest();
  }

  public void testFixNotAvailableIsShown() {
    myGlobalTool.ignoreWhenRefactoringIsComplicated = false;
    doTest();
  }

  public void testUsageCount() {
    myGlobalTool.minimalUsageCount = 5;
    doTest();
  }
}
