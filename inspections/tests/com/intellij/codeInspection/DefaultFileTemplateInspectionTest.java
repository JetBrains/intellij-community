package com.intellij.codeInspection;

import com.intellij.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection;
import com.intellij.testFramework.InspectionTestCase;

public class DefaultFileTemplateInspectionTest extends InspectionTestCase {
  protected void setUp() throws Exception {
    super.setUp();
  }

  private void doTest() throws Exception {
    doTest("defaultFileTemplateUsage/" + getTestName(false), new DefaultFileTemplateUsageInspection());
  }

  public void testDefaultFile() throws Exception{
    doTest();
  }
}
