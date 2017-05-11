/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.testFramework.InspectionTestCase;

public class UnusedReturnValueTest extends InspectionTestCase {
  private UnusedReturnValue myTool = new UnusedReturnValue();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("unusedReturnValue/" + getTestName(true), myTool);
  }


  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testNonLiteral() throws Exception {
    doTest();
  }

  public void testNative() throws Exception {
    doTest();
  }

  public void testHierarchy() throws Exception {
    doTest();
  }

  public void testMethodReference() throws Exception {
    doTest();
  }

  public void testSimpleSetter() throws Exception {
    try {
      myTool.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myTool.IGNORE_BUILDER_PATTERN = false;
    }
  }
}