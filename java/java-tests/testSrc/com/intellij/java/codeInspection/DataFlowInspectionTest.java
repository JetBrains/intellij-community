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
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DataFlowInspectionTest extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testTryInAnonymous() throws Throwable { doTest(); }
  public void testNullableAnonymousMethod() throws Throwable { doTest(); }
  public void testNullableAnonymousParameter() throws Throwable { doTest(); }
  public void testNullableAnonymousVolatile() throws Throwable { doTest(); }
  public void testNullableAnonymousVolatileNotNull() throws Throwable { doTest(); }
  public void testLocalClass() throws Throwable { doTest(); }

  public void testNotNullOnSuperParameter() { doTest(); }

  public void testFieldInAnonymous() throws Throwable { doTest(); }
  public void testFieldInitializerInAnonymous() throws Throwable { doTest(); }
  public void testNullableField() throws Throwable { doTest(); }
  public void testCanBeNullDoesntImplyIsNull() throws Throwable { doTest(); }
  public void testAnnReport() throws Throwable { doTest(); }

  public void testSuppressStaticFlags() throws Throwable { doTest(); }

  public void testBigMethodNotComplex() throws Throwable { doTest(); }
  public void testBuildRegexpNotComplex() throws Throwable { doTest(); }
  public void testTernaryInWhileNotComplex() throws Throwable { doTest(); }
  public void testTryCatchInForNotComplex() throws Throwable { doTest(); }
  public void testTryReturnCatchInWhileNotComplex() throws Throwable { doTest(); }
  public void testNestedTryInWhileNotComplex() throws Throwable { doTest(); }
  public void testExceptionFromFinally() throws Throwable { doTest(); }
  public void testExceptionFromFinallyNesting() throws Throwable { doTest(); }
  public void testNestedFinally() { doTest(); }
  public void testTryFinallyInsideFinally() { doTest(); }
  public void testBreakContinueViaFinally() { doTest(); }
  public void testFieldChangedBetweenSynchronizedBlocks() throws Throwable { doTest(); }

  public void testGeneratedEquals() throws Throwable { doTest(); }

  public void testIDEA84489() throws Throwable { doTest(); }
  public void testComparingNullToNotNull() { doTest(); }
  public void testComparingNullableToNullable() { doTest(); }
  public void testComparingNullableToUnknown() { doTest(); }
  public void testComparingToNotNullShouldNotAffectNullity() throws Throwable { doTest(); }
  public void testComparingToNullableShouldNotAffectNullity() throws Throwable { doTest(); }
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
  public void testSwitchEnumConstant() { doTest(); }
  public void testEnumConstantNotNull() throws Throwable { doTest(); }
  public void testCheckEnumConstantConstructor() { doTest(); }
  public void testCompareToEnumConstant() throws Throwable { doTest(); }
  public void testEqualsConstant() throws Throwable { doTest(); }
  public void testInternedStringConstants() { doTest(); }
  public void testDontSaveTypeValue() { doTest(); }
  public void testFinalLoopVariableInstanceof() throws Throwable { doTest(); }
  public void testGreaterIsNotEquals() throws Throwable { doTest(); }
  public void testNotGreaterIsNotEquals() throws Throwable { doTest(); }

  public void testAnnotationMethodNotNull() { doTest(); }

  public void testChainedFinalFieldsDfa() throws Throwable { doTest(); }
  public void testFinalFieldsDifferentInstances() throws Throwable { doTest(); }
  public void testThisFieldGetters() throws Throwable { doTest(); }
  public void testChainedFinalFieldAccessorsDfa() throws Throwable { doTest(); }
  public void testAccessorPlusMutator() throws Throwable { doTest(); }
  public void testClosureVariableField() throws Throwable { doTest(); }
  public void testOptionalThis() { doTest(); }
  public void testQualifiedThis() { doTest(); }

  public void testAssigningNullableToNotNull() throws Throwable { doTest(); }
  public void testAssigningUnknownToNullable() throws Throwable { doTest(); }
  public void testAssigningClassLiteralToNullable() throws Throwable { doTest(); }

  public void testSynchronizingOnNullable() throws Throwable { doTest(); }
  public void testSwitchOnNullable() { doTest(); }
  public void testReturningNullFromVoidMethod() throws Throwable { doTest(); }
  public void testReturningNullConstant() { doTest(); }
  public void testReturningConstantExpression() { doTest(); }

  public void testCatchRuntimeException() throws Throwable { doTest(); }
  // IDEA-129331
  //public void testCatchThrowable() throws Throwable { doTest(); }
  public void testNotNullCatchParameter() { doTest(); }

  public void testAssertFailInCatch() throws Throwable {
    myFixture.addClass("package org.junit; public class Assert { public static void fail() {}}");
    doTest();
  }

  public void testPreserveNullableOnUncheckedCast() throws Throwable { doTest(); }
  public void testPrimitiveCastMayChangeValue() throws Throwable { doTest(); }

  public void testPassingNullableIntoVararg() throws Throwable { doTest(); }
  public void testEqualsImpliesNotNull() throws Throwable { doTestReportConstantReferences(); }
  public void testEffectivelyUnqualified() throws Throwable { doTest(); }

  public void testQualifierEquality() throws Throwable { doTest(); }

  public void testSkipAssertions() {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.DONT_REPORT_TRUE_ASSERT_STATEMENTS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testParanoidMode() {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testReportConstantReferences() {
    doTestReportConstantReferences();
    String hint = "Replace with 'null'";
    checkIntentionResult(hint);
  }

  private void checkIntentionResult(String hint) {
    myFixture.launchAction(myFixture.findSingleIntention(hint));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testReportConstantReferences_OverloadedCall() {
    doTestReportConstantReferences();
    checkIntentionResult("Replace with 'null'");
  }

  public void testReportConstantReferencesAfterFinalFieldAccess() { doTestReportConstantReferences(); }

  private void doTestReportConstantReferences() {
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testCheckFieldInitializers() {
    doTest();
  }

  public void testConstantDoubleComparisons() { doTest(); }
  public void testInherentNumberRanges() { doTest(); }

  public void testMutableNullableFieldsTreatment() { doTest(); }
  public void testMutableVolatileNullableFieldsTreatment() { doTest(); }
  public void testMutableNotAnnotatedFieldsTreatment() { doTest(); }
  public void testSuperCallMayChangeFields() { doTest(); }
  public void testOtherCallMayChangeFields() { doTest(); }

  public void testMethodCallFlushesField() { doTest(); }
  public void testDoubleNaN() { doTest(); }
  public void testUnknownFloatMayBeNaN() { doTest(); }
  public void testBoxedNaN() { doTest(); }
  public void testFloatEquality() { doTest(); }
  public void testLastConstantConditionInAnd() { doTest(); }
  
  public void testCompileTimeConstant() { doTest(); }
  public void testNoParenthesesWarnings() { doTest(); }

  public void testTransientFinalField() { doTest(); }
  public void testRememberLocalTransientFieldState() { doTest(); }
  public void testFinalFieldDuringInitialization() { doTest(); }
  public void testFinalFieldDuringSuperInitialization() { doTest(); }
  public void testFinalFieldInCallBeforeInitialization() { doTest(); }
  public void testFinalFieldInConstructorAnonymous() { doTest(); }

  public void testFinalFieldNotDuringInitialization() {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true;
    inspection.REPORT_CONSTANT_REFERENCE_VALUES = false;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }


  public void _testSymmetricUncheckedCast() { doTest(); } // https://youtrack.jetbrains.com/issue/IDEABKL-6871
  public void testNullCheckDoesntAffectUncheckedCast() { doTest(); }
  public void testThrowNull() { doTest(); }
  public void testThrowNullable() { doTest(); }

  public void testExplicitlyNullableLocalVar() { doTest(); }

  public void testTryWithResourcesNullability() { doTest(); }
  public void testTryWithResourcesInstanceOf() { doTest(); }
  public void testOmnipresentExceptions() { doTest(); }

  public void testEqualsHasNoSideEffects() { doTest(); }

  public void testHonorGetterAnnotation() { doTest(); }

  public void testIgnoreAssertions() {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.IGNORE_ASSERT_STATEMENTS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testContractAnnotation() { doTest(); }
  public void testContractInapplicableComparison() { doTest(); }
  public void testContractInLoopNotTooComplex() { doTest(); }
  public void testContractWithManyParameters() { doTest(); }
  public void testContractWithNullable() { doTest(); }
  public void testContractWithNotNull() { doTest(); }
  public void testContractPreservesUnknownNullability() { doTest(); }
  public void testContractPreservesUnknownMethodNullability() { doTest(); }
  public void testContractSeveralClauses() { doTest(); }
  public void testContractVarargs() { doTest(); }
  public void testContractConstructor() { doTest(); }
  public void testFlushVariableOnStackToNotNullType() { doTest(); }

  public void testCustomContracts() { doTest(); }

  public void testBoxingImpliesNotNull() { doTest(); }
  public void testLargeIntegersAreNotEqualWhenBoxed() { doTest(); }
  public void testNoGenericCCE() { doTest(); }
  public void testDoubleCCEWarning() { doTest(); }
  public void testLongCircuitOperations() { doTest(); }
  public void testUnconditionalForLoop() { doTest(); }
  public void testIncrementParenthesized() { doTest(); }
  public void testDecrementAnotherObjectField() { doTest(); }
  public void testAnonymousMethodIndependence() { doTest(); }
  public void testAnonymousFieldIndependence() { doTest(); }
  public void testNoConfusionWithAnonymousConstantInitializer() { doTest(); }
  public void testForeachOverWildcards() { doTest(); }
  public void testFinalGetter() { doTest(); }
  public void testGetterResultsNotSame() { doTest(); }
  public void testIntersectionTypeInstanceof() { doTest(); }

  public void testImmutableClassNonGetterMethod() {
    myFixture.addClass("package javax.annotation.concurrent; public @interface Immutable {}");
    doTest();
  }

  public void testByteBufferGetter() {
    myFixture.addClass("package java.nio; public class MappedByteBuffer { public int getInt() {} }");
    doTest();
  }

  public void testManySequentialIfsNotComplex() { doTest(); }
  public void testManySequentialInstanceofsNotComplex() { doTest(); }
  public void testLongDisjunctionsNotComplex() { doTest(); }
  public void testManyDistinctPairsNotComplex() { doTest(); }
  public void testWhileNotComplex() { doTest(); }
  public void testAssertTrueNotComplex() { doTest(); }
  public void testAssertThrowsAssertionError() { doTest(); }
  public void testManyDisjunctiveFieldAssignmentsInLoopNotComplex() { doTest(); }
  public void testManyContinuesNotComplex() { doTest(); }
  public void testFinallyNotComplex() { doTest(); }
  public void testFlushFurtherUnusedVariables() { doTest(); }
  public void testDontFlushVariablesUsedInClosures() { doTest(); }

  public void testVariablesDiverge() { doTest(); }
  public void testMergeByNullability() { doTest(); }
  public void testCheckComplementarityWhenMerge() { doTest(); }
  public void testDontForgetInstanceofInfoWhenMerging() { doTest(); }
  public void testDontForgetEqInfoWhenMergingByType() { doTest(); }
  public void testDontMakeNullableAfterInstanceof() { doTest(); }
  public void testDontMakeUnrelatedVariableNotNullWhenMerging() { doTest(); }
  public void testDontMakeUnrelatedVariableFalseWhenMerging() { doTest(); }
  public void testDontLoseInequalityInformation() { doTest(); }
  
  public void testNotEqualsTypo() { doTest(); }
  public void testAndEquals() { doTest(); }
  public void testXor() { doTest(); }

  public void testUnusedCallDoesNotMakeUnknown() { doTest(); }
  public void testEmptyCallDoesNotMakeNullable() { doTest(); }
  public void testGettersAndPureNoFlushing() { doTest(); }
  public void testFalseGetters() { doTest(); }
  
  public void testNotNullAfterDereference() { doTest(); }

  public void testNullableBoolean() { doTest(); }

  public void testSameComparisonTwice() { doTest(); }
  public void testRootThrowableCause() { doTest(); }

  public void testNotNullOverridesNullable() { doTest(); }

  public void testOverridingInferredNotNullMethod() { doTest(); }
  public void testUseInferredContracts() { doTest(); }
  public void testContractWithNoArgs() { doTest(); }
  public void testContractInferenceBewareOverriding() { doTest(); }

  public void testNumberComparisonsWhenValueIsKnown() { doTest(); }
  public void testFloatComparisons() { doTest(); }
  public void testComparingIntToDouble() { doTest(); }

  public void testNullableArray() { doTest(); }

  public void testAccessingSameArrayElements() { doTest(); }
  public void testArrayLength() { doTest(); }
  public void testForEachOverEmptyCollection() { doTest(); }

  public void testMethodParametersCanChangeNullability() { doTest(); }

  public void testParametersAreNonnullByDefault() {
    addJavaxNullabilityAnnotations(myFixture);
    addJavaxDefaultNullabilityAnnotations(myFixture);
    
    myFixture.addClass("package foo; public class AnotherPackageNotNull { public static void foo(String s) {}}");
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");
    
    doTest(); 
  }

  public void testNullabilityDefaultVsMethodImplementing() {
    addJavaxDefaultNullabilityAnnotations(myFixture);
    
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }
  
  public void testTypeQualifierNickname() {
    addJavaxNullabilityAnnotations(myFixture);

    myFixture.addClass("package bar;" +
                       "import javax.annotation.meta.*;" +
                       "@TypeQualifierNickname() @javax.annotation.NonNull(when = Maybe.MAYBE) " +
                       "public @interface NullableNick {}");
    
    doTest();
  }

  public static void addJavaxDefaultNullabilityAnnotations(final JavaCodeInsightTestFixture fixture) {
    fixture.addClass("package javax.annotation;" +
                     "@javax.annotation.meta.TypeQualifierDefault(java.lang.annotation.ElementType.PARAMETER) @javax.annotation.Nonnull " +
                     "public @interface ParametersAreNonnullByDefault {}");
    fixture.addClass("package javax.annotation;" +
                     "@javax.annotation.meta.TypeQualifierDefault(java.lang.annotation.ElementType.PARAMETER) @javax.annotation.Nullable " +
                     "public @interface ParametersAreNullableByDefault {}");
  }

  public static void addJavaxNullabilityAnnotations(final JavaCodeInsightTestFixture fixture) {
    fixture.addClass("package javax.annotation.meta;" +
                     "public @interface TypeQualifierDefault { java.lang.annotation.ElementType[] value() default {};}");
    fixture.addClass("package javax.annotation.meta;" +
                     "public enum When { ALWAYS, UNKNOWN, MAYBE, NEVER }");
    fixture.addClass("package javax.annotation.meta;" +
                     "public @interface TypeQualifierNickname {}");

    fixture.addClass("package javax.annotation;" +
                     "import javax.annotation.meta.*;" +
                     "public @interface Nonnull {" +
                     "  When when() default When.ALWAYS;" +
                     "}");
    fixture.addClass("package javax.annotation;" +
                     "import javax.annotation.meta.*;" +
                     "@TypeQualifierNickname " +
                     "@Nonnull(when = When.UNKNOWN) " +
                     "public @interface Nullable {}");
  }

  public void testCustomTypeQualifierDefault() {
    addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package bar;" +
                       "@javax.annotation.meta.TypeQualifierDefault(java.lang.annotation.ElementType.METHOD) @javax.annotation.Nonnull " +
                       "public @interface MethodsAreNotNullByDefault {}");

    myFixture.addClass("package foo; public class AnotherPackageNotNull { public static native Object foo(String s); }");
    myFixture.addFileToProject("foo/package-info.java", "@bar.MethodsAreNotNullByDefault package foo;");

    myFixture.enableInspections(new DataFlowInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testCustomDefaultInEnums() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault({PARAMETER, FIELD, METHOD, LOCAL_VARIABLE}) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface NonnullByDefault {}");

    myFixture.addFileToProject("foo/package-info.java", "@NonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(new DataFlowInspection());
    myFixture.checkHighlighting(true, false, true);
  }

  public void testTrueOrEqualsSomething() {
    doTest();
    checkIntentionResult("Remove redundant assignment");
  }

  public void testDontSimplifyAssignment() {
    doTest();
    assertEmpty(myFixture.filterAvailableIntentions("Simplify"));
  }

  public void testVolatileFieldNPEFixes() {
    doTest();
    assertEmpty(myFixture.filterAvailableIntentions("Surround"));
    assertEmpty(myFixture.filterAvailableIntentions("Assert"));
    assertNotEmpty(myFixture.filterAvailableIntentions("Introduce variable"));
  }

  public void _testNullCheckBeforeInstanceof() { doTest(); } // https://youtrack.jetbrains.com/issue/IDEA-113220

  public void testConstantConditionsWithAssignmentsInside() { doTest(); }
  public void testIfConditionsWithAssignmentInside() { doTest(); }
  public void testBitwiseNegatedBoxed() { doTest(); }
  public void testDontShadowFinalReassignment() { doTest(); }

  public void testLiteralIfCondition() {
    doTest();
    myFixture.findSingleIntention("Remove 'if' statement");
  }

  public void testLiteralWhileCondition() {
    doTest();
    checkIntentionResult("Remove 'while' statement");
  }

  public void testLiteralDoWhileCondition() {
    doTest();
    checkIntentionResult("Unwrap 'do-while' statement");
  }
  public void testLiteralDoWhileConditionWithBreak() {
    doTest();
    assertFalse(myFixture.getAvailableIntentions().stream().anyMatch(i -> i.getText().contains("Unwrap 'do-while' statement")));
  }

  public void testFalseForConditionNoInitialization() {
    doTest();
    checkIntentionResult("Remove 'for' statement");
  }

  public void testFalseForConditionWithInitialization() {
    doTest();
    checkIntentionResult("Remove 'for' statement");
  }

  public void testUsingInterfaceConstant() { doTest();}

  //https://youtrack.jetbrains.com/issue/IDEA-162184
  public void testNullLiteralAndInferredMethodContract() {
    doTest();
  }
  public void testNullLiteralArgumentDoesntReportedWhenMethodOnlyThrowAnException() { doTest(); }
  public void testNullLiteralArgumentValueUsedAsReturnValue() {
    doTest();
  }

  public void testCapturedWildcardNotNull() { doTest(); }

  public void testNullableMethodReturningNotNull() { doTest(); }

  public void testDivisionByZero() { doTestReportConstantReferences(); }

  public void testFieldUsedBeforeInitialization() { doTest(); }

  public void testImplicitlyInitializedField() {
    PlatformTestUtil.registerExtension(ImplicitUsageProvider.EP_NAME, new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitRead(PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitlyNotNullInitialized(@NotNull PsiElement element) {
        return element instanceof PsiField && ((PsiField)element).getName().startsWith("field");
      }
    }, myFixture.getTestRootDisposable());
    doTest();
  }

  public void testNoNonSensicalFixesOnCastedNull() {
    doTest();
    assertEmpty(ContainerUtil.findAll(myFixture.getAvailableIntentions(), i -> i.getText().contains("null")));
  }

  public void testEmptySingletonMap() {doTest();}
}
