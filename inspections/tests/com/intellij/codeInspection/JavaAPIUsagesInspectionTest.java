/*
 * User: anna
 * Date: 11-Sep-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.testFramework.InspectionTestCase;

public class JavaAPIUsagesInspectionTest extends InspectionTestCase {

  private void doTest() throws Exception {
    doTest("usage1.5/" + getTestName(false), new LocalInspectionToolWrapper(new Java15APIUsageInspection()), "java 1.5");
  }

  public void testConstructor() throws Exception {
    doTest();
  }
}