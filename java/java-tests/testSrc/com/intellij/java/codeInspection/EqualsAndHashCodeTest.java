// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.equalsAndHashcode.EqualsAndHashcode;
import com.intellij.testFramework.JavaInspectionTestCase;

public class EqualsAndHashCodeTest extends JavaInspectionTestCase {
  
  private EqualsAndHashcode myTool = new EqualsAndHashcode();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest("equalsAndHashcode/" + getTestName(true), myTool);
  }


  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testInnerClass() {
    doTest();
  }

  public void testHierarchy() {
    doTest();
  }

  public void testRecord() {
    doTest();
  }
}
