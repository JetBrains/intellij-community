/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DataFlowRangeAnalysisTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8_ANNOTATED);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  @Test
  public void testLongRangeBasics() { dataFlow.get().doTest(); }

  @Test
  public void testLongRangeLoop() { dataFlow.get().doTest(); }
  @Test
  public void testCountedLoopWithOverflow() { dataFlow.get().doTest(); }

  @Test
  public void testLongRangeAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("""
                         package javax.annotation;

                         public @interface Nonnegative {}""");
    dataFlow.get().doTest();
  }

  @Test
  public void testLongRangeKnownMethods() {
    dataFlow.get().doTest();
  }
  @Test
  public void testStringSubstring() {
    dataFlow.get().doTest();
  }

  @Test
  public void testLongRangeMod() { dataFlow.get().doTest(); }
  @Test
  public void testLongRangeDivShift() { dataFlow.get().doTest(); }

  @Test
  public void testLongRangePlusMinus() { dataFlow.get().doTest(); }
  @Test
  public void testLongRangeMul() { dataFlow.get().doTest(); }
  @Test
  public void testFebruary31() { dataFlow.get().doTest(); }

  @Test
  public void testManyAdditionsDoNotCauseExponentialBlowUp() { dataFlow.get().doTest(); }
  @Test
  public void testBoxedRanges() { dataFlow.get().doTest(); }
  @Test
  public void testLongRangeDiff() { dataFlow.get().doTest(); }
  @Test
  public void testIntLongTypeConversion() { dataFlow.get().doTest(); }
  @Test
  public void testBackPropagation() { dataFlow.get().doTest(); }
  @Test
  public void testTwoArraysDiff() { dataFlow.get().doTest(); }
  @Test
  public void testModRange() { dataFlow.get().doTest(); }
  @Test
  public void testBackPropagationMod() { dataFlow.get().doTest(); }
  @Test
  public void testModPlus() { dataFlow.get().doTest(); }
  @Test
  public void testArithmeticNoOp() { dataFlow.get().doTest(); }
  @Test
  public void testStringConcat() { dataFlow.get().doTest(); }
  @Test
  public void testUnaryPlusMinus() { dataFlow.get().doTest(); }
  @Test
  public void testWidenPlusInLoop() { dataFlow.get().doTest(); }
  @Test
  public void testFloatLoop() { dataFlow.get().doTest(); }
  @Test
  public void testWidenMulInLoop() { dataFlow.get().doTest(); }
  @Test
  public void testReduceBinOpOnCast() { dataFlow.get().doTest(); }
  @Test
  public void testSuppressZeroReport() { dataFlow.get().doTest(); }
  @Test
  public void testCompareMethods() { dataFlow.get().doTest(); }
  @Test
  public void testWidenMismatch() { dataFlow.get().doTest(); }
  @Test
  public void testDontWidenPlusInLoop() { dataFlow.get().doTest(); }
  @Test
  public void testCollectionAddRemove() { dataFlow.get().doTest(); }

  @Test
  public void testCollectionRemoveIf() { dataFlow.get().doTest(); }

  @Test
  public void testRelationsOnAddition() { dataFlow.get().doTest(); }
  @Test
  public void testModSpecialCase() { dataFlow.get().doTest(); }
  @Test
  public void testArrayAccessWithCastInCountedLoop() { dataFlow.get().doTest(); }
  @Test
  public void testFloatingPointRanges() { dataFlow.get().doTest(); }
  @Test
  public void testFloatingPointCasts() { dataFlow.get().doTest(); }
  @Test
  public void testFloatingPointMaxLoop() { dataFlow.get().doTest(); }
  @Test
  public void testFloatingPointConstantMath() { dataFlow.get().doTest(); }
  @Test
  public void testStringIndexOfRelation() { dataFlow.get().doTest(); }
  @Test
  public void testIncompleteLoop() { dataFlow.get().doTest(); }
  @Test
  public void testTwoFlagsMixed() { dataFlow.get().doTest(); }
  @Test
  public void testArrayInitializersWithMethodCall() { dataFlow.get().doTest(); }
  @Test
  public void testLongMinValue() { dataFlow.get().doTest(); }
  @Test
  public void testArrayBinarySearchNoWarningAtNeedle() { dataFlow.get().doTest(); }

  @Test
  public void testJmhParamRange() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package org.openjdk.jmh.annotations;public @interface Param {String[] value();}");
    dataFlow.get().doTest();
  }
}
