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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * @author peter
 */
public class DataFlowInspectionFixtureTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.HIGHEST);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  private void doTest() throws Throwable {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testTryInAnonymous() throws Throwable { doTest(); }
  public void testNullableAnonymousMethod() throws Throwable { doTest(); }
  public void testNullableAnonymousParameter() throws Throwable { doTest(); }
  public void testNullableAnonymousVolatile() throws Throwable { doTest(); }
  public void testNullableAnonymousVolatileNotNull() throws Throwable { doTest(); }

  public void testFieldInAnonymous() throws Throwable { doTest(); }
  public void testNullableField() throws Throwable { doTest(); }
  public void testCanBeNullDoesntImplyIsNull() throws Throwable { doTest(); }
  public void testAnnReport() throws Throwable { doTest(); }

  public void testBigMethodNotComplex() throws Throwable { doTest(); }
  public void testBuildRegexpNotComplex() throws Throwable { doTest(); }
  public void testTernaryInWhileNotComplex() throws Throwable { doTest(); }
  public void testTryCatchInForNotComplex() throws Throwable { doTest(); }
  public void testFieldChangedBetweenSynchronizedBlocks() throws Throwable { doTest(); }

  public void testGeneratedEquals() throws Throwable { doTest(); }

  public void testIDEA84489() throws Throwable { doTest(); }
  public void testComparingToNotNullShouldNotAffectNullity() throws Throwable { doTest(); }
  public void testStringTernaryAlwaysTrue() throws Throwable { doTest(); }

  public void testBoxing128() throws Throwable { doTest(); }
  public void testFinalFieldsInitializedByAnnotatedParameters() throws Throwable { doTest(); }
  public void testMultiCatch() throws Throwable { doTest(); }
  public void testContinueFlushesLoopVariable() throws Throwable { doTest(); }

  public void testEqualsNotNull() throws Throwable { doTest(); }
  public void testVisitFinallyOnce() throws Throwable { doTest(); }
  public void testNotEqualsDoesntImplyNotNullity() throws Throwable { doTest(); }
  public void testEqualsEnumConstant() throws Throwable { doTest(); }
  public void testEqualsConstant() throws Throwable { doTest(); }
  public void testFinalLoopVariableInstanceof() throws Throwable { doTest(); }
  public void testGreaterIsNotEquals() throws Throwable { doTest(); }
  public void testNotGreaterIsNotEquals() throws Throwable { doTest(); }

  public void testChainedFinalFieldsDfa() throws Throwable { doTest(); }
  public void testFinalFieldsDifferentInstances() throws Throwable { doTest(); }
  public void testChainedFinalFieldAccessorsDfa() throws Throwable { doTest(); }

  public void testAssigningUnknownToNullable() throws Throwable { doTest(); }
  public void testAssigningClassLiteralToNullable() throws Throwable { doTest(); }

  public void testSynchronizingOnNullable() throws Throwable { doTest(); }
  public void testReturningNullFromVoidMethod() throws Throwable { doTest(); }

  public void testCatchRuntimeException() throws Throwable { doTest(); }

}
