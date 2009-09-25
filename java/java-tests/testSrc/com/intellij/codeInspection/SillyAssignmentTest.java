/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 25-Jul-2006
 * Time: 12:07:22
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.testFramework.InspectionTestCase;

public class SillyAssignmentTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("sillyAssignment/" + getTestName(true),  new SillyAssignmentInspection());
  }

  public void testMultiple() throws Exception {
    doTest();
  }

  public void testSuppressed() throws Exception {
    doTest();
  }
}