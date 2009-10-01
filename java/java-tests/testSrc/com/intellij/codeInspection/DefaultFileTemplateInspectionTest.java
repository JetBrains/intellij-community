package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection;
import com.intellij.testFramework.InspectionTestCase;

public class DefaultFileTemplateInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("defaultFileTemplateUsage/" + getTestName(true), new DefaultFileTemplateUsageInspection());
  }

  public void testDefaultFile() throws Exception{
    doTest();
  }
}
