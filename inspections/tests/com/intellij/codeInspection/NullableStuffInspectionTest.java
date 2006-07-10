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
  private void doTest() throws Exception {
    doTest("nullableProblems/" + getTestName(true), new LocalInspectionToolWrapper(new NullableStuffInspection()),"java 1.5");
  }

  public void testProblems() throws Exception{ doTest(); }
}