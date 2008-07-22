package com.intellij.codeInspection;

import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author ven
 */
public class FieldCanBeLocalTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("fieldCanBeLocal/" + getTestName(true), new FieldCanBeLocalInspection());
  }

  public void testSimple () throws Exception { doTest(); }

  public void testTwoMethods () throws Exception { doTest(); }

  public void testConstructor () throws Exception { doTest(); }
  public void testStaticFinal() throws Exception { doTest(); }
  public void testStaticAccess() throws Exception { doTest(); }
}
