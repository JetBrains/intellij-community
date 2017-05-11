/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.equalsAndHashcode.EqualsAndHashcode;
import com.intellij.testFramework.InspectionTestCase;

public class EqualsAndHashCodeTest extends InspectionTestCase {
  private EqualsAndHashcode myTool = new EqualsAndHashcode();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("equalsAndHashcode/" + getTestName(true), myTool);
  }


  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testInnerClass() throws Exception {
    doTest();
  }

  public void testHierarchy() throws Exception {
    doTest();
  }

}
