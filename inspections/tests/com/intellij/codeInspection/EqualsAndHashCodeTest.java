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

import com.intellij.codeInspection.equalsAndHashcode.EqualsAndHashcode;
import com.intellij.testFramework.InspectionTestCase;

public class EqualsAndHashCodeTest extends InspectionTestCase {
  private EqualsAndHashcode myTool = new EqualsAndHashcode();

  protected void setUp() throws Exception {
    super.setUp();
    myTool.projectOpened(getProject());
  }


  protected void tearDown() throws Exception {
    myTool.projectClosed(getProject());
    super.tearDown();
  }

  private void doTest() throws Exception {
    doTest("equalsAndHashcode/" + getTestName(false), myTool);
  }


  public void testInnerClass() throws Exception {
    doTest();
  }

  public void testHierarchy() throws Exception {
    doTest();
  }

}