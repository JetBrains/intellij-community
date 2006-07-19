package com.intellij.codeInspection;

import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author ven
 */
public class FieldCanBeLocalTest extends InspectionTestCase {
  protected void setUp() throws Exception {
    super.setUp();
  }

  private void doTest() throws Exception {
    doTest("fieldCanBeLocal/" + getTestName(true), new FieldCanBeLocalInspection());
  }

  public void testSimple () throws Exception {
    doTest();
  }

  public void testTwoMethods () throws Exception {
    doTest();
  }
}
