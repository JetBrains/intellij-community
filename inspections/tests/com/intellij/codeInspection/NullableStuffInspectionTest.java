/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 08.07.2006
 * Time: 0:07:45
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.testFramework.InspectionTestCase;

public class NullableStuffInspectionTest extends InspectionTestCase {
  private final NullableStuffInspection myInspection = new NullableStuffInspection();
  {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = false;
  }

  private void doTest() throws Exception {
    doTest("nullableProblems/" + getTestName(true), new LocalInspectionToolWrapper(myInspection),"java 1.5");
  }
  private void doTest14() throws Exception {
    doTest("nullableProblems/" + getTestName(true), new LocalInspectionToolWrapper(myInspection),"java 1.4");
  }

  public void testProblems() throws Exception{ doTest(); }
  public void testProblems2() throws Exception{ doTest(); }
  public void testJdk14() throws Exception{ doTest14(); }

  public void testGetterSetterProblems() throws Exception{ doTest(); }
  public void testOverriddenMethods() throws Exception{
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }
}