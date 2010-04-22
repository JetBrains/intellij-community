package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author ven
 */
public class FieldCanBeLocalTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("fieldCanBeLocal/" + getTestName(true), new FieldCanBeLocalInspection());
  }

  public void testSimple () throws Exception { doTest(); }

  public void testTwoMethods () throws Exception { doTest(); }

  public void testConstructor () throws Exception { doTest(); }
  public void testStaticFinal() throws Exception { doTest(); }
  public void testStaticAccess() throws Exception { doTest(); }
  public void testInnerClassConstructor() throws Exception { doTest(); }
  public void testLocalVar2InnerClass() throws Exception { doTest(); }
  public void testStateField() throws Exception { doTest(); }
  public void testLocalStateVar2InnerClass() throws Exception { doTest(); }
  public void testNotConstantInitializer() throws Exception {doTest();}
  public void testInnerClassFieldInitializer() throws Exception {doTest();}
}
