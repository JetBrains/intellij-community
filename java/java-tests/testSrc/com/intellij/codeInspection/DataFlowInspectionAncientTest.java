/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 * @since Apr 11, 2002
 */
public class DataFlowInspectionAncientTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest(false);
  }
  private void doTest(boolean lowercase) {
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.REPORT_CONSTANT_REFERENCE_VALUES = false;
    doTest("dataFlow/" + getTestName(lowercase), inspection);
  }

  private void doTest15() {
    doTest15(false);
  }
  private void doTest15(boolean lowercase) {
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.REPORT_CONSTANT_REFERENCE_VALUES = false;
    doTest("dataFlow/" + getTestName(lowercase), inspection, "java 1.5");
  }

  public void testNpe1() { doTest(true); }
  public void testCaseAndNpe() { doTest(true); }
  public void testCce() { doTest(true); }
  public void testExceptionCFG() { doTest(true); }
  public void testInst() { doTest(true); }
  public void testWrongEqualTypes() { doTest(true); }
  public void testSCR13702() { doTest(); }
  public void testSCR13626() { doTest(); }
  public void testSCR13871() { doTest(); }
  public void testInstanceof() { doTest(); }
  public void testOrBug() { doTest(true); }
  public void testSCR14819() { doTest(); }
  public void testSCR14314() { doTest(); }
  public void testSCR15162() { doTest(); }
  public void testCatchParameterCantBeNull() { doTest(); }
  public void testXor() { doTest(true); }
  public void testGenericInstanceof() { doTest(); }
  public void testThisInstanceof() { doTest(true); }
  public void testAndEq() { doTest(true); }
  public void testNullableField() { doTest(true); }
  public void testSCR39950() { doTest(); }
  public void testScrIDEA1() { doTest(true); }
  public void testSCR18186() { doTest(); }
  public void testConstantExpr() { doTest(true); }
  public void testIDEADEV74518() { doTest(); }
  public void testIDEADEV74518_2() { doTest(); }
  public void testIDEADEV77819() { doTest(); }
  public void testIDEADEV78370() { doTest(); }
  public void testRegressionByPti() { doTest(); }

  public void testNotNullable() { doTest15(); }
  public void testNotNullableParameter() { doTest15(); }
  public void testNotNullableParameter2() { doTest15(); }
  public void testNullable() { doTest15(); }
  public void testNullableThroughCast() { doTest15(); }
  public void testNullableProblemThroughCast() { doTest15(); }
  public void testNullableThroughVariable() { doTest15(); }
  public void testNullableThroughVariableShouldNotBeReported() { doTest15(); }
  public void testNullableLocalVariable() { doTest15(); }
  public void testNotNullLocalVariable() { doTest15(); }
  public void testNullableReturn() { doTest15(); }
  public void testNullableReturn1() { doTest15(); }
  public void testFinalFields() { doTest15(true); }
  public void testNotNullArray() { doTest15(); }
  public void testFieldsFlashing() { doTest15(); }
  public void testConditionFalseAndNPE() { doTest15(); }
  public void testIDEADEV1575() { doTest15(); }
  public void testAlexBug() { doTest15(); }
  public void testYoleBug() { doTest15(); }
  public void testForeachFlow() { doTest15(); }
  public void testForEachNPE() { doTest15(); }
  public void testArrayAccessNPE() { doTest15(); }
  public void testArrayAccessDoesntCancelAnalysis() { doTest15(); }
  public void testCompileTimeConst() { doTest15(true); }
  public void testAutoboxing() { doTest15(true); }
  public void testUnboxingNPE() { doTest15(true); }
  public void testStrangeArrayIndexOutOfBounds() { doTest15(); }
  public void testIDEADEV2605() { doTest15(); }
  public void testConstantsDifferentTypes() { doTest15(); }
  public void testBoxingNaN() { doTest15(); }
  public void testBoxingBoolean() { doTest15(true); }
  public void testCheckedExceptionDominance() { doTest15(); }
  public void testIDEADEV10489() { doTest15(); }
  public void testPlusOnStrings() { doTest15(); }
  public void testSwitchQualifierProducesNPE() {doTest15(); }
  public void testIDEADEV15583() {doTest15(); }
  public void testIDEADEV13153() { doTest15(); }
  public void testIDEADEV13156() { doTest15(); }
  public void testSwitchEnumCases() { doTest15(); }

  public void testSCR15406() { doTest(); }
}
