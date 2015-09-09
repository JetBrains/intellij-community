/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 07-Aug-2006
 * Time: 20:34:37
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.equalsAndHashcode.EqualsAndHashcode;
import com.intellij.testFramework.InspectionTestCase;

public class EqualsAndHashCodeTest extends InspectionTestCase {
  private final EqualsAndHashcode myTool = new EqualsAndHashcode();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("equalsAndHashcode/" + getTestName(true), myTool);
  }


  public void testInnerClass() throws Exception {
    doTest();
  }

  public void testHierarchy() throws Exception {
    doTest();
  }

}
