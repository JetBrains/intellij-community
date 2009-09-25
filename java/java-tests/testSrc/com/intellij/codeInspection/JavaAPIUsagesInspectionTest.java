/*
 * User: anna
 * Date: 11-Sep-2007
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.testFramework.InspectionTestCase;

public class JavaAPIUsagesInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("usage1.5/" + getTestName(false), new LocalInspectionToolWrapper(new Java15APIUsageInspection()), "java 1.5");
  }

  public void testConstructor() throws Exception {
    doTest();
  }

  public void testIgnored() throws Exception {
    doTest();
  }
}