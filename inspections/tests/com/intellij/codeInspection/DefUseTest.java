package com.intellij.codeInspection;

import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.testFramework.InspectionTestCase;

public class DefUseTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("defUse/" + getTestName(false), new DefUseInspection());
  }


  public void testSCR5144() throws Exception {
    doTest();
  }

  public void testSCR6843() throws Exception {
    doTest();
  }

  public void testunusedVariable() throws Exception {
    doTest();
  }

  public void testarrayIndexUsages() throws Exception {
    doTest();
  }
  /* TODO:
  public void testSCR28019() throws Exception {
    doTest();
  }
  */

  public void testSCR40364() throws Exception {
    doTest();
  }

  public void testArrayLength() throws Exception {
    doTest();
  }

  public void testUsedInArrayInitializer() throws Exception {
    doTest();
  }
}
