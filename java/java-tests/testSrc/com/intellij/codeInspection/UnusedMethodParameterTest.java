/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 07-Nov-2006
 * Time: 13:13:59
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unusedParameters.UnusedParametersInspection;
import com.intellij.testFramework.InspectionTestCase;

public class UnusedMethodParameterTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private final UnusedParametersInspection myTool = new UnusedParametersInspection();

  private void doTest() throws Exception {
    doTest("unusedMethodParameter/" + getTestName(true), myTool);
  }

  public void testFieldInAnonymousClass() throws Exception {
    doTest();
  }

  public void testUnusedParameter() throws Exception {
    doTest();
  }

  public void testUsedForReading() throws Exception {
    doTest();
  }

  public void testSuppressedParameter() throws Exception {
    doTest();
  }

  public void testEntryPointUnusedParameter() throws Exception {
    doTest("unusedMethodParameter/" + getTestName(true), myTool, true, true);
  }

  public void testAppMainUnusedParams() throws Exception {
    doTest();
  }
}
