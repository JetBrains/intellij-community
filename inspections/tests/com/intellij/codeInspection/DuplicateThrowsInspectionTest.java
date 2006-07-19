package com.intellij.codeInspection;

import com.intellij.codeInspection.duplicateThrows.DuplicateThrowsInspection;
import com.intellij.testFramework.InspectionTestCase;

public class DuplicateThrowsInspectionTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("duplicateThrows/" + getTestName(true), new DuplicateThrowsInspection());
  }

  public void testSimple() throws Exception{
    doTest();
  }
}
