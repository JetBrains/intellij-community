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

import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.testFramework.InspectionTestCase;

public class UnusedReturnValueTest extends InspectionTestCase {
  private final UnusedReturnValue myTool = new UnusedReturnValue();


  private void doTest() throws Exception {
    doTest("unusedReturnValue/" + getTestName(false), myTool);
  }


  public void testnonLiteral() throws Exception {
    doTest();
  }

  public void testHierarchy() throws Exception {
    doTest();
  }

}