// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DataFlowInspectionAncientTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/ancient");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspection inspection = new DataFlowInspection();
    ConstantValueInspection cvInspection = new ConstantValueInspection();
    cvInspection.REPORT_CONSTANT_REFERENCE_VALUES = false;
    myFixture.enableInspections(inspection, cvInspection);
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testNpe1() { doTest(); }
  @Test
  public void testCaseAndNpe() { doTest(); }
  @Test
  public void testCce() { doTest(); }
  @Test
  public void testExceptionCFG() { doTest(); }
  @Test
  public void testInst() { doTest(); }
  @Test
  public void testWrongEqualTypes() { doTest(); }
  @Test
  public void testSCR13626() { doTest(); }
  @Test
  public void testSCR13871() { doTest(); }
  @Test
  public void testInstanceof() { doTest(); }
  @Test
  public void testOrBug() { doTest(); }
  @Test
  public void testSCR14819() { doTest(); }
  @Test
  public void testSCR14314() { doTest(); }
  @Test
  public void testSCR15162() { doTest(); }
  @Test
  public void testCatchParameterCantBeNull() { doTest(); }
  @Test
  public void testXor() { doTest(); }
  @Test
  public void testGenericInstanceof() { doTest(); }
  @Test
  public void testThisInstanceof() { doTest(); }
  @Test
  public void testAndEq() { doTest(); }
  @Test
  public void testNullableField() { doTest(); }
  @Test
  public void testSCR39950() { doTest(); }
  @Test
  public void testScrIDEA1() { doTest(); }
  @Test
  public void testSCR18186() { doTest(); }
  @Test
  public void testConstantExpr() { doTest(); }
  @Test
  public void testIDEADEV74518() { doTest(); }
  @Test
  public void testIDEADEV74518_2() { doTest(); }
  @Test
  public void testIDEADEV77819() { doTest(); }
  @Test
  public void testIDEADEV78370() { doTest(); }
  @Test
  public void testRegressionByPti() { doTest(); }

  @Test
  public void testNotNullable() { doTest(); }
  @Test
  public void testNotNullableParameter() { doTest(); }
  @Test
  public void testNotNullableParameter2() { doTest(); }
  @Test
  public void testNullable() { doTest(); }
  @Test
  public void testNullableThroughCast() { doTest(); }
  @Test
  public void testNullableProblemThroughCast() { doTest(); }
  @Test
  public void testNullableThroughVariable() { doTest(); }
  @Test
  public void testNullableThroughVariableShouldNotBeReported() { doTest(); }
  @Test
  public void testNullableLocalVariable() { doTest(); }
  @Test
  public void testNotNullLocalVariable() { doTest(); }
  @Test
  public void testNullableReturn() { doTest(); }
  @Test
  public void testNullableReturn1() { doTest(); }
  @Test
  public void testFinalFields() { doTest(); }
  @Test
  public void testNotNullArray() { doTest(); }
  @Test
  public void testFieldsFlashing() { doTest(); }
  @Test
  public void testConditionFalseAndNPE() { doTest(); }
  @Test
  public void testIDEADEV1575() { doTest(); }
  @Test
  public void testAlexBug() { doTest(); }
  @Test
  public void testYoleBug() { doTest(); }
  @Test
  public void testForeachFlow() { doTest(); }
  @Test
  public void testForEachNPE() { doTest(); }
  @Test
  public void testArrayAccessNPE() { doTest(); }
  @Test
  public void testArrayAccessDoesntCancelAnalysis() { doTest(); }
  @Test
  public void testAutoboxing() { doTest(); }
  @Test
  public void testUnboxingNPE() { doTest(); }
  @Test
  public void testStrangeArrayIndexOutOfBounds() { doTest(); }
  @Test
  public void testIDEADEV2605() { doTest(); }
  @Test
  public void testConstantsDifferentTypes() { doTest(); }
  @Test
  public void testBoxingNaN() { doTest(); }
  @Test
  public void testCheckedExceptionDominance() { doTest(); }
  @Test
  public void testIDEADEV10489() { doTest(); }
  @Test
  public void testPlusOnStrings() { doTest(); }
  @Test
  public void testSwitchQualifierProducesNPE() {doTest(); }
  @Test
  public void testIDEADEV15583() {doTest(); }
  @Test
  public void testIDEADEV13153() { doTest(); }
  @Test
  public void testIDEADEV13156() { doTest(); }
  @Test
  public void testSwitchEnumCases() { doTest(); }

  @Test
  public void testSCR15406() { doTest(); }
  @Test
  public void testWrongParameter() { doTest(); }
}
