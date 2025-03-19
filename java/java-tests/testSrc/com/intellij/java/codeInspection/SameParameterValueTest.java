// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.util.AccessModifier;
import com.intellij.testFramework.JavaInspectionTestCase;

public class SameParameterValueTest extends JavaInspectionTestCase {
  private SameParameterValueInspection myTool = new SameParameterValueInspection();

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private String getTestDir() {
    return "sameParameterValue/" + getTestName(true);
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testEntryPoint() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testWithoutDeadCode() {
    AccessModifier previous = myTool.highestModifier;
    myTool.highestModifier = AccessModifier.PUBLIC;
    try {
      doTest(getTestDir(), myTool, false, false);
    } finally {
      myTool.highestModifier = previous;
    }
  }

  public void testVarargs() {
    myTool.ignoreWhenRefactoringIsComplicated = false;
    doTest(getTestDir(), myTool, false, true);
  }

  public void testSimpleVararg() {
    boolean previous = myTool.ignoreWhenRefactoringIsComplicated;
    try {
      myTool.ignoreWhenRefactoringIsComplicated = false;
      doTest(getTestDir(), myTool, false, true);
    } finally {
      myTool.ignoreWhenRefactoringIsComplicated = previous;
    }
  }
  
  public void testMethodWithSuper() {
    AccessModifier previous = myTool.highestModifier;
    myTool.highestModifier = AccessModifier.PUBLIC;
    try {
      doTest(getTestDir(), myTool, false, true);
    } finally {
      myTool.highestModifier = previous;
    }
  }

  public void testNotReportedDueToHighVisibility() {
    doTest(getTestDir(), myTool, false, false);
  }

  public void testNegativeDouble() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testClassObject() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testUsageCount() {
    int previous = myTool.minimalUsageCount;
    try {
      myTool.minimalUsageCount = 5;
      doTest(getTestDir(), myTool, false, true);
    }
    finally {
      myTool.minimalUsageCount = previous;
    }
  }

  public void testField() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testOverrideGroovy() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testMethodReferences() {
    doTest(getTestDir(), myTool, false, true);
  }
}
