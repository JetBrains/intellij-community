package com.intellij.codeInspection;

import com.intellij.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.idea.Bombed;

import java.util.Calendar;

@Bombed(year = 2006,month = Calendar.DECEMBER, day = 1,time = 14,user = "cdr")
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
