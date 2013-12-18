/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.ConditionCheckManager;
import com.intellij.codeInsight.ConditionChecker;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author peter
 */
public class DataFlowInspectionTest extends LightCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  private void doTest() {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    inspection.REPORT_CONSTANT_REFERENCE_VALUES = false;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testTryInAnonymous() throws Throwable { doTest(); }
  public void testNullableAnonymousMethod() throws Throwable { doTest(); }
  public void testNullableAnonymousParameter() throws Throwable { doTest(); }
  public void testNullableAnonymousVolatile() throws Throwable { doTest(); }
  public void testNullableAnonymousVolatileNotNull() throws Throwable { doTest(); }
  public void testLocalClass() throws Throwable { doTest(); }

  public void testFieldInAnonymous() throws Throwable { doTest(); }
  public void testFieldInitializerInAnonymous() throws Throwable { doTest(); }
  public void testNullableField() throws Throwable { doTest(); }
  public void testCanBeNullDoesntImplyIsNull() throws Throwable { doTest(); }
  public void testAnnReport() throws Throwable { doTest(); }

  public void testBigMethodNotComplex() throws Throwable { doTest(); }
  public void testBuildRegexpNotComplex() throws Throwable { doTest(); }
  public void testTernaryInWhileNotComplex() throws Throwable { doTest(); }
  public void testTryCatchInForNotComplex() throws Throwable { doTest(); }
  public void testNestedTryInWhileNotComplex() throws Throwable { doTest(); }
  public void testExceptionFromFinally() throws Throwable { doTest(); }
  public void testExceptionFromFinallyNesting() throws Throwable { doTest(); }
  public void testNestedFinally() { doTest(); }
  public void testFieldChangedBetweenSynchronizedBlocks() throws Throwable { doTest(); }

  public void testGeneratedEquals() throws Throwable { doTest(); }

  public void testIDEA84489() throws Throwable { doTest(); }
  public void testComparingToNotNullShouldNotAffectNullity() throws Throwable { doTest(); }
  public void testStringTernaryAlwaysTrue() throws Throwable { doTest(); }
  public void testStringConcatAlwaysNotNull() throws Throwable { doTest(); }

  public void testNotNullPrimitive() throws Throwable { doTest(); }
  public void testBoxing128() throws Throwable { doTest(); }
  public void testFinalFieldsInitializedByAnnotatedParameters() throws Throwable { doTest(); }
  public void testFinalFieldsInitializedNotNull() throws Throwable { doTest(); }
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
  public void testThisFieldGetters() throws Throwable { doTest(); }
  public void testChainedFinalFieldAccessorsDfa() throws Throwable { doTest(); }
  public void testAccessorPlusMutator() throws Throwable { doTest(); }
  public void testClosureVariableField() throws Throwable { doTest(); }

  public void testAssigningNullableToNotNull() throws Throwable { doTest(); }
  public void testAssigningUnknownToNullable() throws Throwable { doTest(); }
  public void testAssigningClassLiteralToNullable() throws Throwable { doTest(); }

  public void testSynchronizingOnNullable() throws Throwable { doTest(); }
  public void testReturningNullFromVoidMethod() throws Throwable { doTest(); }

  public void testCatchRuntimeException() throws Throwable { doTest(); }
  public void testCatchThrowable() throws Throwable { doTest(); }
  public void testNotNullCatchParameter() { doTest(); }

  public void testAssertFailInCatch() throws Throwable {
    myFixture.addClass("package org.junit; public class Assert { public static void fail() {}}");
    doTest();
  }

  public void testPreserveNullableOnUncheckedCast() throws Throwable { doTest(); }
  public void testPrimitiveCastMayChangeValue() throws Throwable { doTest(); }

  public void testPassingNullableIntoVararg() throws Throwable { doTest(); }
  public void testEqualsImpliesNotNull() throws Throwable { doTest(); }
  public void testEffectivelyUnqualified() throws Throwable { doTest(); }

  public void testSkipAssertions() {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.DONT_REPORT_TRUE_ASSERT_STATEMENTS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testReportConstantReferences() {
    doTestReportConstantReferences();
    myFixture.launchAction(myFixture.findSingleIntention("Replace with 'null'"));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testReportConstantReferencesAfterFinalFieldAccess() { doTestReportConstantReferences(); }

  private void doTestReportConstantReferences() {
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void _testReportConstantReferences_ReplaceWithString() {
    doTestReportConstantReferences();
    myFixture.launchAction(myFixture.findSingleIntention("Replace with 'CONST'"));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }
  public void _testReportConstantReferences_ReplaceWithIntConstant() {
    doTestReportConstantReferences();
    myFixture.launchAction(myFixture.findSingleIntention("Replace with 'CONST'"));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }
  public void _testReportConstantReferences_ReplaceWithEnum() {
    myFixture.addClass("package foo; public enum MyEnum { FOO }");
    doTestReportConstantReferences();
    myFixture.launchAction(myFixture.findSingleIntention("Replace with 'FOO'"));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }
  public void _testReportConstantReferences_NotInComplexAssignment() {
    doTestReportConstantReferences();
    assertEmpty(myFixture.filterAvailableIntentions("Replace with"));
  }
  public void _testReportConstantReferences_Switch() { doTestReportConstantReferences(); }

  public void testCheckFieldInitializers() {
    doTest();
  }

  public void testConstantDoubleComparisons() { doTest(); }

  public void testMutableNullableFieldsTreatment() { doTest(); }
  public void testMutableVolatileNullableFieldsTreatment() { doTest(); }
  public void testMutableNotAnnotatedFieldsTreatment() { doTest(); }
  public void testSuperCallMayChangeFields() { doTest(); }
  public void testOtherCallMayChangeFields() { doTest(); }

  public void testMethodCallFlushesField() { doTest(); }
  public void testUnknownFloatMayBeNaN() { doTest(); }
  public void testLastConstantConditionInAnd() { doTest(); }

  public void testTransientFinalField() { doTest(); }
  public void _testSymmetricUncheckedCast() { doTest(); } // http://youtrack.jetbrains.com/issue/IDEABKL-6871
  public void testNullCheckDoesntAffectUncheckedCast() { doTest(); }
  public void testThrowNull() { doTest(); }

  public void testTryWithResourcesNullability() { doTest(); }
  public void testTryWithResourcesInstanceOf() { doTest(); }
  public void testOmnipresentExceptions() { doTest(); }

  public void testEqualsHasNoSideEffects() { doTest(); }

  public void testHonorGetterAnnotation() { doTest(); }

  public void testIsNullCheck() throws Exception {
    ConditionCheckManager.getInstance(myModule.getProject()).getIsNullCheckMethods().add(
      buildConditionChecker("Value", "isNull", ConditionChecker.Type.IS_NULL_METHOD,
                            "public class Value { public static boolean isNull(Value o) {if (o == null) return true; else return false;} }"));
    doTest();
  }

  public void testIsNotNullCheck() throws Exception {
    ConditionCheckManager.getInstance(myModule.getProject()).getIsNotNullCheckMethods().add(
      buildConditionChecker("Value", "isNotNull", ConditionChecker.Type.IS_NOT_NULL_METHOD,
                            "public class Value { public static boolean isNotNull(Value o) {if (o == null) return false; else return true;} }"));
    doTest();
  }

  public void testAssertTrue() throws Exception {
    ConditionCheckManager.getInstance(myModule.getProject()).getAssertTrueMethods().add(
      buildConditionChecker("Assertions", "assertTrue", ConditionChecker.Type.ASSERT_TRUE_METHOD,
                            "public class Assertions { public static boolean assertTrue(boolean b) {if(!b) throw new Exception();} }"));
    doTest();
  }

  public void testAssertFalse() throws Exception {
    ConditionCheckManager.getInstance(myModule.getProject()).getAssertFalseMethods().add(
      buildConditionChecker("Assertions", "assertFalse", ConditionChecker.Type.ASSERT_FALSE_METHOD,
                            "public class Assertions { public static boolean assertFalse(boolean b) {if(b) throw new Exception();} }"));
    doTest();
  }

  public void testAssertIsNull() throws Exception {
    ConditionCheckManager.getInstance(myModule.getProject()).getAssertIsNullMethods().add(
      buildConditionChecker("Assertions", "assertIsNull", ConditionChecker.Type.ASSERT_IS_NULL_METHOD,
                            "public class Assertions { public static boolean assertIsNull(Object o) {if(o != null) throw new Exception();} }"));
    doTest();
  }

  public void testAssertIsNotNull() throws Exception {
    ConditionCheckManager.getInstance(myModule.getProject()).getAssertIsNotNullMethods().add(
      buildConditionChecker("Assertions", "assertIsNotNull", ConditionChecker.Type.ASSERT_IS_NOT_NULL_METHOD,
                            "public class Assertions { public static boolean assertIsNotNull(Object o) {if(o == null) throw new Exception();} }"));
    doTest();
  }

  @Nullable
  private ConditionChecker buildConditionChecker(String className, String methodName, ConditionChecker.Type type, String classText)
    throws IOException {
    myFixture.addClass(classText);
    PsiClass psiClass = myFixture.findClass(className);
    PsiMethod psiMethod = null;
    PsiMethod[] methods = psiClass.getMethods();
    for (PsiMethod tempPsiMethod : methods) {
      if (tempPsiMethod.getName().equals(methodName)) {
        psiMethod = tempPsiMethod;
        break;
      }
    }
    assert psiMethod != null;
    return new ConditionChecker.FromPsiBuilder(psiMethod, psiMethod.getParameterList().getParameters()[0], type).build();
  }

  public void testIgnoreAssertions() {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.IGNORE_ASSERT_STATEMENTS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testContractAnnotation() { doTest(); }
  public void testContractInLoopNotTooComplex() { doTest(); }
  public void testContractWithNullable() { doTest(); }
  public void testContractPreservesUnknownNullability() { doTest(); }
  public void testContractSeveralClauses() { doTest(); }

  public void testBoxingImpliesNotNull() { doTest(); }
  public void testLargeIntegersAreNotEqualWhenBoxed() { doTest(); }
  public void testNoGenericCCE() { doTest(); }
  public void testLongCircuitOperations() { doTest(); }
  public void testUnconditionalForLoop() { doTest(); }
  public void testAnonymousMethodIndependence() { doTest(); }
  public void testAnonymousFieldIndependence() { doTest(); }
  public void testNoConfusionWithAnonymousConstantInitializer() { doTest(); }
  public void testForeachOverWildcards() { doTest(); }
  public void testFinalGetter() { doTest(); }
  
  public void testManySequentialIfsNotComplex() { doTest(); }
  public void testManySequentialInstanceofsNotComplex() { doTest(); }
  public void testLongDisjunctionsNotComplex() { doTest(); }
  public void testWhileNotComplex() { doTest(); }
  public void testManyDisjunctiveFieldAssignmentsInLoopNotComplex() { doTest(); }
  public void testManyContinuesNotComplex() { doTest(); }
  public void testFinallyNotComplex() { doTest(); }

  public void testVariablesDiverge() { doTest(); }
  public void testMergeByNullability() { doTest(); }
  public void testDontForgetInstanceofInfoWhenMerging() { doTest(); }
  public void testDontForgetEqInfoWhenMergingByType() { doTest(); }
  public void testDontMakeNullableAfterInstanceof() { doTest(); }
  public void testDontMakeUnrelatedVariableNotNullWhenMerging() { doTest(); }
  public void testDontMakeUnrelatedVariableFalseWhenMerging() { doTest(); }
  public void testDontLoseInequalityInformation() { doTest(); }
  
  public void testNotEqualsTypo() { doTest(); }
  
  public void _testNullCheckBeforeInstanceof() { doTest(); } // http://youtrack.jetbrains.com/issue/IDEA-113220
}
