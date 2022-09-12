// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspectionAncientTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/dataFlow/ancient";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  private void doTest() {
    DataFlowInspection inspection = new DataFlowInspection();
    ConstantValueInspection cvInspection = new ConstantValueInspection();
    cvInspection.REPORT_CONSTANT_REFERENCE_VALUES = false;
    myFixture.enableInspections(inspection, cvInspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testNpe1() { doTest(); }
  public void testCaseAndNpe() { doTest(); }
  public void testCce() { doTest(); }
  public void testExceptionCFG() { doTest(); }
  public void testInst() { doTest(); }
  public void testWrongEqualTypes() { doTest(); }
  public void testSCR13626() { doTest(); }
  public void testSCR13871() { doTest(); }
  public void testInstanceof() { doTest(); }
  public void testOrBug() { doTest(); }
  public void testSCR14819() { doTest(); }
  public void testSCR14314() { doTest(); }
  public void testSCR15162() { doTest(); }
  public void testCatchParameterCantBeNull() { doTest(); }
  public void testXor() { doTest(); }
  public void testGenericInstanceof() { doTest(); }
  public void testThisInstanceof() { doTest(); }
  public void testAndEq() { doTest(); }
  public void testNullableField() { doTest(); }
  public void testSCR39950() { doTest(); }
  public void testScrIDEA1() { doTest(); }
  public void testSCR18186() { doTest(); }
  public void testConstantExpr() { doTest(); }
  public void testIDEADEV74518() { doTest(); }
  public void testIDEADEV74518_2() { doTest(); }
  public void testIDEADEV77819() { doTest(); }
  public void testIDEADEV78370() { doTest(); }
  public void testRegressionByPti() { doTest(); }

  public void testNotNullable() { doTest(); }
  public void testNotNullableParameter() { doTest(); }
  public void testNotNullableParameter2() { doTest(); }
  public void testNullable() { doTest(); }
  public void testNullableThroughCast() { doTest(); }
  public void testNullableProblemThroughCast() { doTest(); }
  public void testNullableThroughVariable() { doTest(); }
  public void testNullableThroughVariableShouldNotBeReported() { doTest(); }
  public void testNullableLocalVariable() { doTest(); }
  public void testNotNullLocalVariable() { doTest(); }
  public void testNullableReturn() { doTest(); }
  public void testNullableReturn1() { doTest(); }
  public void testFinalFields() { doTest(); }
  public void testNotNullArray() { doTest(); }
  public void testFieldsFlashing() { doTest(); }
  public void testConditionFalseAndNPE() { doTest(); }
  public void testIDEADEV1575() { doTest(); }
  public void testAlexBug() { doTest(); }
  public void testYoleBug() { doTest(); }
  public void testForeachFlow() { doTest(); }
  public void testForEachNPE() { doTest(); }
  public void testArrayAccessNPE() { doTest(); }
  public void testArrayAccessDoesntCancelAnalysis() { doTest(); }
  public void testAutoboxing() { doTest(); }
  public void testUnboxingNPE() { doTest(); }
  public void testStrangeArrayIndexOutOfBounds() { doTest(); }
  public void testIDEADEV2605() { doTest(); }
  public void testConstantsDifferentTypes() { doTest(); }
  public void testBoxingNaN() { doTest(); }
  public void testCheckedExceptionDominance() { doTest(); }
  public void testIDEADEV10489() { doTest(); }
  public void testPlusOnStrings() { doTest(); }
  public void testSwitchQualifierProducesNPE() {doTest(); }
  public void testIDEADEV15583() {doTest(); }
  public void testIDEADEV13153() { doTest(); }
  public void testIDEADEV13156() { doTest(); }
  public void testSwitchEnumCases() { doTest(); }

  public void testSCR15406() { doTest(); }
  public void testWrongParameter() { doTest(); }
}
