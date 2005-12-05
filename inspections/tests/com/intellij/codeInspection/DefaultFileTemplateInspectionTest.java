package com.intellij.codeInspection;

import com.intellij.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;


public class DefaultFileTemplateInspectionTest extends InspectionTestCase {
  private LocalInspectionToolWrapper myTool;

  protected void setUp() throws Exception {
    super.setUp();
    myTool = new LocalInspectionToolWrapper(new DefaultFileTemplateUsageInspection());
    myTool.initialize(getManager());
  }

  private void doTest() throws Exception {
    doTest("defaultFileTemplateUsage/" + getTestName(false), myTool);
  }

  public void testDefaultFile() throws Exception{
    doTest();
  }


}
