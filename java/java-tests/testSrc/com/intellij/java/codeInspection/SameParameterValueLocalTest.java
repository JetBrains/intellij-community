// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private String getGlobalTestDir() {
    return "sameParameterValue/" + getTestName(true);
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testEntryPoint() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testMethodWithSuper() {
    AccessModifier previous = myGlobalTool.highestModifier;
    myGlobalTool.highestModifier = AccessModifier.PUBLIC;
    try {
      doTest(getGlobalTestDir(), myTool);
    } finally {
      myGlobalTool.highestModifier = previous;
    }
  }

  public void testNotReportedDueToHighVisibility() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testVarargs() {
    myGlobalTool.ignoreWhenRefactoringIsComplicated = false;
    doTest(getGlobalTestDir(), myTool);
  }

  public void testNativeMethod() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testNegativeDouble() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testMethodReferenceInCallArguments() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testFixAvailable() { doTest(getGlobalTestDir(), myTool); }

  public void testFixNotAvailable() { doTest(getGlobalTestDir(), myTool); }

  public void testFixNotAvailableIsShown() {
    boolean previous = myGlobalTool.ignoreWhenRefactoringIsComplicated;
    try {
      myGlobalTool.ignoreWhenRefactoringIsComplicated = false;
      doTest(getGlobalTestDir(), myTool);
    } finally {
      myGlobalTool.ignoreWhenRefactoringIsComplicated = previous;
    }
  }

  public void testUsageCount() {
    int previous = myGlobalTool.minimalUsageCount;
    try {
      myGlobalTool.minimalUsageCount = 5;
      doTest(getGlobalTestDir(), myTool);
    }
    finally {
      myGlobalTool.minimalUsageCount = previous;
    }
  }

  public void testOverrideGroovy() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testMethodReferences() {
    doTest(getGlobalTestDir(), myTool);
  }
}
