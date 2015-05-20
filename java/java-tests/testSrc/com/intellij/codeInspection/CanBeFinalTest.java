/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 7:51:16 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.canBeFinal.CanBeFinalInspection;
import com.intellij.testFramework.InspectionTestCase;

public class CanBeFinalTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    final CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = true;
    tool.REPORT_FIELDS = true;
    tool.REPORT_METHODS = true;
    doTest(tool);
  }

  private void doTest(final CanBeFinalInspection tool) throws Exception {
    doTest("canBeFinal/" + getTestName(false), tool);
  }

  public void testsimpleClassInheritanceField() throws Exception {
    doTest();
  }

  public void testsimpleClassInheritance() throws Exception {
    doTest();
  }

  public void testsimpleClassInheritance1() throws Exception {
    doTest();
  }

  public void testanonymous() throws Exception {
    doTest();
  }

  public void testmethodInheritance() throws Exception {
    doTest();
  }

  public void testprivateInners() throws Exception {
    doTest();
  }

  public void testfieldAndTryBlock() throws Exception {
    doTest();
  }

  public void testfields() throws Exception {
    doTest();
  }

  public void testfieldsReading() throws Exception {
    doTest();
  }

  public void testSCR6073() throws Exception {
    doTest();
  }

  public void testSCR6781() throws Exception {
    doTest();
  }

  public void testSCR6845() throws Exception {
    doTest();
  }

  public void testSCR6861() throws Exception {
    doTest();
  }

  public void testfieldAssignmentssInInitializer() throws Exception {
    doTest();
  }

  public void teststaticFields() throws Exception {
    doTest();
  }

  public void teststaticClassInitializer() throws Exception {
    doTest();
  }

  public void testSCR7737() throws Exception {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = false;
    tool.REPORT_METHODS = true;

    doTest(tool);
  }

  public void testInterfaceMethodInHierarchy() throws Exception {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = false;
    tool.REPORT_METHODS = true;

    doTest(tool);
  }
}
