// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class DataFlowInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7_ANNOTATED);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  @Test
  public void testTryInAnonymous() { dataFlow.get().doTest(); }
  @Test
  public void testNullableAnonymousMethod() { dataFlow.get().doTest(); }
  @Test
  public void testNullableAnonymousParameter() { dataFlow.get().doTest(); }
  @Test
  public void testNullableAnonymousVolatile() { dataFlow.get().doTest(); }
  @Test
  public void testNullableAnonymousVolatileNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testLocalClass() { dataFlow.get().doTest(); }

  @Test
  public void testNotNullOnSuperParameter() { dataFlow.get().doTest(); }
  @Test
  public void testNullableOnSuperParameter() { dataFlow.get().doTest(); }
  @Test
  public void testNullableOnSuperParameterNoInference() { dataFlow.get().doTest(); }

  @Test
  public void testFieldInAnonymous() { dataFlow.get().doTest(); }
  @Test
  public void testFieldInitializerInAnonymous() { dataFlow.get().doTest(); }
  @Test
  public void testNullableField() { dataFlow.get().doTest(); }
  @Test
  public void testCanBeNullDoesntImplyIsNull() { dataFlow.get().doTest(); }
  @Test
  public void testAnnReport() { dataFlow.get().doTest(); }

  @Test
  public void testSuppressStaticFlags() { dataFlow.get().doTest(); }

  @Test
  public void testBigMethodNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testBuildRegexpNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testTernaryInWhileNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testTryCatchInForNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testTryReturnCatchInWhileNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testNestedTryInWhileNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testExceptionFromFinally() { dataFlow.get().doTest(); }
  @Test
  public void testExceptionFromFinallyNesting() { dataFlow.get().doTest(); }
  @Test
  public void testNestedFinally() { dataFlow.get().doTest(); }
  @Test
  public void testTryFinallyInsideFinally() { dataFlow.get().doTest(); }
  @Test
  public void testBreakContinueViaFinally() { dataFlow.get().doTest(); }
  @Test
  public void testFieldChangedBetweenSynchronizedBlocks() { dataFlow.get().doTest(); }

  @Test
  public void testGeneratedEquals() { dataFlow.get().doTest(); }

  @Test
  public void testIDEA84489() { dataFlow.get().doTest(); }
  @Test
  public void testComparingNullToNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testComparingNullableToNullable() { dataFlow.get().doTest(); }
  @Test
  public void testComparingNullableToUnknown() { dataFlow.get().doTest(); }
  @Test
  public void testComparingToNotNullShouldNotAffectNullity() { dataFlow.get().doTest(); }
  @Test
  public void testComparingToNullableShouldNotAffectNullity() { dataFlow.get().doTest(); }
  @Test
  public void testStringTernaryAlwaysTrue() { dataFlow.get().doTest(); }
  @Test
  public void testStringConcatAlwaysNotNull() { dataFlow.get().doTest(); }

  @Test
  public void testNotNullPrimitive() { dataFlow.get().doTest(); }
  @Test
  public void testBoxing128() { dataFlow.get().doTest(); }
  @Test
  public void testFinalFieldsInitializedByAnnotatedParameters() { dataFlow.get().doTest(); }
  @Test
  public void testFinalFieldsInitializedNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testMultiCatch() { dataFlow.get().doTest(); }
  @Test
  public void testContinueFlushesLoopVariable() { dataFlow.get().doTest(); }

  @Test
  public void testEqualsNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testVisitFinallyOnce() { dataFlow.get().doTest(); }
  @Test
  public void testNotEqualsDoesntImplyNotNullity() { dataFlow.get().doTest(); }
  @Test
  public void testEqualsEnumConstant() { dataFlow.get().doTest(); }
  @Test
  public void testSwitchEnumConstant() { dataFlow.get().doTest(); }
  @Test
  public void testEphemeralDefaultCaseVisited() { dataFlow.get().doTest(); }
  @Test
  public void testEphemeralInIfChain() { dataFlow.get().doTest(); }
  @Test
  public void testIncompleteSwitchEnum() { dataFlow.get().doTest(); }
  @Test
  public void testEnumConstantNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testCheckEnumConstantConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testCompareToEnumConstant() { dataFlow.get().doTest(); }
  @Test
  public void testEqualsConstant() { dataFlow.get().doTest(); }
  @Test
  public void testInternedStringConstants() { dataFlow.get().doTest(); }
  @Test
  public void testDontSaveTypeValue() { dataFlow.get().doTest(); }
  @Test
  public void testFinalLoopVariableInstanceof() { dataFlow.get().doTest(); }
  @Test
  public void testGreaterIsNotEquals() { dataFlow.get().doTest(); }
  @Test
  public void testNotGreaterIsNotEquals() { dataFlow.get().doTest(); }

  @Test
  public void testAnnotationMethodNotNull() { dataFlow.get().doTest(); }

  @Test
  public void testChainedFinalFieldsDfa() { dataFlow.get().doTest(); }
  @Test
  public void testFinalFieldsDifferentInstances() { dataFlow.get().doTest(); }
  @Test
  public void testThisFieldGetters() { dataFlow.get().doTest(); }
  @Test
  public void testChainedFinalFieldAccessorsDfa() { dataFlow.get().doTest(); }
  @Test
  public void testAccessorPlusMutator() { dataFlow.get().doTest(); }
  @Test
  public void testClosureVariableField() { dataFlow.get().doTest(); }
  @Test
  public void testOptionalThis() { dataFlow.get().doTest(); }
  @Test
  public void testQualifiedThis() { dataFlow.get().doTest(); }

  @Test
  public void testAssigningNullableToNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testAssigningUnknownToNullable() { dataFlow.get().doTest(); }
  @Test
  public void testAssigningClassLiteralToNullable() { dataFlow.get().doTest(); }

  @Test
  public void testSynchronizingOnNullable() { dataFlow.get().doTest(); }
  @Test
  public void testSwitchOnNullable() { dataFlow.get().doTest(); }
  @Test
  public void testReturningNullFromVoidMethod() { dataFlow.get().doTest(); }
  @Test
  public void testReturningNullConstant() { dataFlow.get().doTest(); }
  @Test
  public void testReturningConstantExpression() { dataFlow.get().doTest(); }

  @Test
  public void testCatchRuntimeException() { dataFlow.get().doTest(); }
  // IDEA-129331
  //public void testCatchThrowable() throws Throwable { doTest(); }
  @Test
  public void testNotNullCatchParameter() { dataFlow.get().doTest(); }

  @Test
  public void testAssertFailInCatch() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package org.junit; public class Assert { public static void fail() {}}");
    dataFlow.get().doTest();
  }

  @Test
  public void testPreserveNullableOnUncheckedCast() { dataFlow.get().doTest(); }
  @Test
  public void testPrimitiveCastMayChangeValue() { dataFlow.get().doTest(); }

  @Test
  public void testPassingNullableIntoVararg() { dataFlow.get().doTest(); }
  @Test
  public void testEqualsImpliesNotNull() {
    dataFlow.get().doTestWith((i, _) -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
  }
  @Test
  public void testEffectivelyUnqualified() { dataFlow.get().doTest(); }

  @Test
  public void testQualifierEquality() { dataFlow.get().doTest(); }

  @Test
  public void testSkipAssertions() {
    dataFlow.get().doTestWith((_, i) -> {
      i.DONT_REPORT_TRUE_ASSERT_STATEMENTS = true;
      i.REPORT_CONSTANT_REFERENCE_VALUES = true;
    });
  }

  @Test
  public void testParanoidMode() {
    dataFlow.get().doTestWith((i, _) -> i.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true);
  }

  @Test
  public void testReportConstantReferences() {
    dataFlow.get().doTestWith((i, _) -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
    String hint = "Replace with 'null'";
    checkIntentionResult(hint);
  }

  private void checkIntentionResult(String hint) {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention(hint));
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(dataFlow.get().getTestName(false) + "_after.java");
      PsiTestUtil.checkPsiMatchesTextIgnoringNonCode(myFixture.getFile());
    });
  }

  @Test
  public void testReportConstantReferences_OverloadedCall() {
    dataFlow.get().doTestWith((i, _) -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
    checkIntentionResult("Replace with 'null'");
  }

  @Test
  public void testReportConstantReferencesAfterFinalFieldAccess() {
    dataFlow.get().doTestWith((i, _) -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
  }

  @Test
  public void testCheckFieldInitializers() {
    dataFlow.get().doTest();
  }

  @Test
  public void testConstantDoubleComparisons() { dataFlow.get().doTest(); }
  @Test
  public void testInherentNumberRanges() { dataFlow.get().doTest(); }

  @Test
  public void testMutableNullableFieldsTreatment() { dataFlow.get().doTest(); }
  @Test
  public void testMutableVolatileNullableFieldsTreatment() { dataFlow.get().doTest(); }
  @Test
  public void testMutableNotAnnotatedFieldsTreatment() { dataFlow.get().doTest(); }
  @Test
  public void testSuperCallMayChangeFields() { dataFlow.get().doTest(); }
  @Test
  public void testOtherCallMayChangeFields() { dataFlow.get().doTest(); }

  @Test
  public void testMethodCallFlushesField() { dataFlow.get().doTest(); }
  @Test
  public void testDoubleNaN() { dataFlow.get().doTest(); }
  @Test
  public void testDoubleNaN2() { dataFlow.get().doTest(); }
  @Test
  public void testUnknownFloatMayBeNaN() { dataFlow.get().doTest(); }
  @Test
  public void testBoxedNaN() { dataFlow.get().doTest(); }
  @Test
  public void testFloatEquality() { dataFlow.get().doTest(); }
  @Test
  public void testLastConstantConditionInAnd() { dataFlow.get().doTest(); }

  @Test
  public void testCompileTimeConstant() { dataFlow.get().doTest(); }
  @Test
  public void testNoParenthesesWarnings() { dataFlow.get().doTest(); }

  @Test
  public void testTransientFinalField() { dataFlow.get().doTest(); }
  @Test
  public void testRememberLocalTransientFieldState() { dataFlow.get().doTest(); }
  @Test
  public void testFinalFieldDuringInitialization() { dataFlow.get().doTest(); }
  @Test
  public void testFinalFieldDuringSuperInitialization() { dataFlow.get().doTest(); }
  @Test
  public void testFinalFieldInCallBeforeInitialization() { dataFlow.get().doTest(); }
  @Test
  public void testFinalFieldInConstructorAnonymous() { dataFlow.get().doTest(); }

  @Test
  public void testFinalFieldNotDuringInitialization() {
    dataFlow.get().doTestWith((i, cv) -> {
      i.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true;
      cv.REPORT_CONSTANT_REFERENCE_VALUES = false;
    });
  }

  @Test
  @Disabled("IDEABKL-6871")
  public void testSymmetricUncheckedCast() { dataFlow.get().doTest(); }
  @Test
  public void testNullCheckDoesntAffectUncheckedCast() { dataFlow.get().doTest(); }
  @Test
  public void testThrowNull() { dataFlow.get().doTest(); }
  @Test
  public void testThrowNullable() { dataFlow.get().doTest(); }

  @Test
  public void testExplicitlyNullableLocalVar() { dataFlow.get().doTest(); }

  @Test
  public void testTryWithResourcesNullability() { dataFlow.get().doTest(); }
  @Test
  public void testTryWithResourcesInstanceOf() { dataFlow.get().doTest(); }
  @Test
  public void testTryWithResourcesCloseException() { dataFlow.get().doTest(); }
  @Test
  public void testTryWithResourceExpressions() { dataFlow.get().doTest(); }

  @Test
  public void testOmnipresentExceptions() { dataFlow.get().doTest(); }

  @Test
  public void testEqualsHasNoSideEffects() { dataFlow.get().doTest(); }

  @Test
  public void testHonorGetterAnnotation() { dataFlow.get().doTest(); }

  @Test
  public void testIgnoreAssertions() {
    dataFlow.get().doTestWith((_, i) -> i.IGNORE_ASSERT_STATEMENTS = true);
  }

  @Test
  public void testContractAnnotation() { dataFlow.get().doTest(); }
  @Test
  public void testContractInapplicableComparison() { dataFlow.get().doTest(); }
  @Test
  public void testContractInLoopNotTooComplex() { dataFlow.get().doTest(); }
  @Test
  public void testContractWithManyParameters() { dataFlow.get().doTest(); }
  @Test
  public void testContractWithNullable() { dataFlow.get().doTest(); }
  @Test
  public void testContractWithNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testContractPreservesUnknownNullability() { dataFlow.get().doTest(); }
  @Test
  public void testContractPreservesUnknownMethodNullability() { dataFlow.get().doTest(); }
  @Test
  public void testContractSeveralClauses() { dataFlow.get().doTest(); }
  @Test
  public void testContractVarargs() { dataFlow.get().doTest(); }
  @Test
  public void testContractConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testFlushVariableOnStackToNotNullType() { dataFlow.get().doTest(); }

  @Test
  public void testCustomContracts() { dataFlow.get().doTest(); }

  @Test
  public void testBoxingImpliesNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testLargeIntegersAreNotEqualWhenBoxed() { dataFlow.get().doTest(); }
  @Test
  public void testNoGenericCCE() { dataFlow.get().doTest(); }
  @Test
  public void testDoubleCCEWarning() { dataFlow.get().doTest(); }
  @Test
  public void testLongCircuitOperations() { dataFlow.get().doTest(); }
  @Test
  public void testUnconditionalForLoop() { dataFlow.get().doTest(); }
  @Test
  public void testIncrementParenthesized() { dataFlow.get().doTest(); }
  @Test
  public void testDecrementAnotherObjectField() { dataFlow.get().doTest(); }
  @Test
  public void testAnonymousMethodIndependence() { dataFlow.get().doTest(); }
  @Test
  public void testAnonymousFieldIndependence() { dataFlow.get().doTest(); }
  @Test
  public void testNoConfusionWithAnonymousConstantInitializer() { dataFlow.get().doTest(); }
  @Test
  public void testForeachOverWildcards() { dataFlow.get().doTest(); }
  @Test
  public void testFinalGetter() { dataFlow.get().doTest(); }
  @Test
  public void testGetterResultsNotSame() { dataFlow.get().doTest(); }
  @Test
  public void testIntersectionTypeInstanceof() { dataFlow.get().doTest(); }

  @Test
  public void testKeepComments() {
    dataFlow.get().doTest();
    checkIntentionResult("Simplify");
  }

  @Test
  public void testImmutableClassNonGetterMethod() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package com.google.auto.value; public @interface AutoValue {}");
    myFixture.addClass("package javax.annotation.concurrent; public @interface Immutable {}");
    dataFlow.get().doTest();
  }

  @Test
  public void testByteBufferGetter() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package java.nio; public class MappedByteBuffer { public int getInt() {} }");
    dataFlow.get().doTest();
  }

  @Test
  public void testManySequentialIfsNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testManySequentialInstanceofsNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testLongDisjunctionsNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testManyDistinctPairsNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testWhileNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testAssertTrueNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testAssertThrowsAssertionError() { dataFlow.get().doTest(); }
  @Test
  public void testManyDisjunctiveFieldAssignmentsInLoopNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testManyContinuesNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testFinallyNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testFlushFurtherUnusedVariables() { dataFlow.get().doTest(); }
  @Test
  public void testDontFlushVariablesUsedInClosures() { dataFlow.get().doTest(); }

  @Test
  public void testVariablesDiverge() { dataFlow.get().doTest(); }
  @Test
  public void testMergeByNullability() { dataFlow.get().doTest(); }
  @Test
  public void testCheckComplementarityWhenMerge() { dataFlow.get().doTest(); }
  @Test
  public void testDontForgetInstanceofInfoWhenMerging() { dataFlow.get().doTest(); }
  @Test
  public void testDontForgetEqInfoWhenMergingByType() { dataFlow.get().doTest(); }
  @Test
  public void testDontMakeNullableAfterInstanceof() { dataFlow.get().doTest(); }
  @Test
  public void testDontMakeUnrelatedVariableNotNullWhenMerging() { dataFlow.get().doTest(); }
  @Test
  public void testDontMakeUnrelatedVariableFalseWhenMerging() { dataFlow.get().doTest(); }
  @Test
  public void testDontLoseInequalityInformation() { dataFlow.get().doTest(); }

  @Test
  public void testNotEqualsTypo() { dataFlow.get().doTest(); }
  @Test
  public void testAndEquals() { dataFlow.get().doTest(); }
  @Test
  public void testXor() { dataFlow.get().doTest(); }

  @Test
  public void testUnusedCallDoesNotMakeUnknown() { dataFlow.get().doTest(); }
  @Test
  public void testEmptyCallDoesNotMakeNullable() { dataFlow.get().doTest(); }
  @Test
  public void testGettersAndPureNoFlushing() { dataFlow.get().doTest(); }
  @Test
  public void testFalseGetters() { dataFlow.get().doTest(); }

  @Test
  public void testNotNullAfterDereference() { dataFlow.get().doTest(); }

  @Test
  public void testNullableBoolean() { dataFlow.get().doTest(); }

  @Test
  public void testSameComparisonTwice() { dataFlow.get().doTest(); }
  @Test
  public void testRootThrowableCause() { dataFlow.get().doTest(); }

  @Test
  public void testNotNullOverridesNullable() { dataFlow.get().doTest(); }

  @Test
  public void testOverridingInferredNotNullMethod() { dataFlow.get().doTest(); }
  @Test
  public void testUseInferredContracts() { dataFlow.get().doTest(); }
  @Test
  public void testContractWithNoArgs() { dataFlow.get().doTest(); }
  @Test
  public void testContractInferenceBewareOverriding() { dataFlow.get().doTest(); }

  @Test
  public void testNumberComparisonsWhenValueIsKnown() { dataFlow.get().doTest(); }
  @Test
  public void testFloatComparisons() { dataFlow.get().doTest(); }
  @Test
  public void testComparingIntToDouble() { dataFlow.get().doTest(); }

  @Test
  public void testNullableArray() { dataFlow.get().doTest(); }

  @Test
  public void testAccessingSameArrayElements() { dataFlow.get().doTest(); }
  @Test
  public void testArrayLength() { dataFlow.get().doTest(); }
  @Test
  public void testForEachOverEmptyCollection() { dataFlow.get().doTest(); }

  @Test
  public void testMethodParametersCanChangeNullability() { dataFlow.get().doTest(); }

  @Test
  public void testParametersAreNonnullByDefault() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJavaxNullabilityAnnotations(myFixture);
    addJavaxDefaultNullabilityAnnotations(myFixture);

    myFixture.addClass("package foo; public class AnotherPackageNotNull { public static void foo(String s) {}}");
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    dataFlow.get().doTest();
  }
  
  @Test
  public void testJsr305CheckForNullAsQualifierNickname() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJavaxNullabilityAnnotations(myFixture);
    addJavaxDefaultNullabilityAnnotations(myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testNullabilityDefaultVsMethodImplementing() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJavaxDefaultNullabilityAnnotations(myFixture);
    dataFlow.get().doTestWith((i, _) -> i.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true);
  }

  @Test
  public void testTypeQualifierNickname() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass(barNullableNick());

    dataFlow.get().doTest();
  }

  @Test
  public void testTypeQualifierNicknameWithoutDeclarations() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass(barNullableNick());

    myFixture.enableInspections(new DataFlowInspection());
    myFixture.testHighlighting(true, false, true, "TypeQualifierNickname.java");
  }

  static String barNullableNick() {
    return "package bar;" +
           "@javax.annotation.meta.TypeQualifierNickname() " +
           "@javax.annotation.Nonnull(when = javax.annotation.meta.When.MAYBE) " +
           "public @interface NullableNick {}";
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
    fixture.addClass("package javax.annotation.meta; public @interface TypeQualifierNickname {}");
    fixture.addClass("package javax.annotation.meta;" +
                     "public @interface TypeQualifierDefault { java.lang.annotation.ElementType[] value() default {};}");
    fixture.addClass("package javax.annotation.meta;" +
                     "public enum When { ALWAYS, UNKNOWN, MAYBE, NEVER }");

    fixture.addClass("""
                       package javax.annotation;
                       import javax.annotation.meta.*;
                       public @interface Nonnull {
                         When when() default When.ALWAYS;
                       }""");
    fixture.addClass("""
                       package javax.annotation;
                       import javax.annotation.meta.*;
                       @TypeQualifierNickname
                       @Nonnull(when = When.MAYBE)
                       public @interface CheckForNull {}""");
    fixture.addClass("""
                       package javax.annotation;
                       import javax.annotation.meta.*;
                       @TypeQualifierNickname
                       @Nonnull(when = When.UNKNOWN)
                       public @interface Nullable {}""");
  }

  @Test
  public void testCustomTypeQualifierDefault() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package bar;" +
                       "@javax.annotation.meta.TypeQualifierDefault(java.lang.annotation.ElementType.METHOD) @javax.annotation.Nonnull " +
                       "public @interface MethodsAreNotNullByDefault {}");

    myFixture.addClass("package foo; public class AnotherPackageNotNull { public static native Object foo(String s); }");
    myFixture.addFileToProject("foo/package-info.java", "@bar.MethodsAreNotNullByDefault package foo;");

    myFixture.enableInspections(new DataFlowInspection(), new ConstantValueInspection());
    myFixture.testHighlighting(true, false, true, dataFlow.get().getTestName(false) + ".java");
  }

  @Test
  public void testCustomDefaultInEnums() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault({PARAMETER, FIELD, METHOD, LOCAL_VARIABLE}) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface NonnullByDefault {}");

    myFixture.addFileToProject("foo/package-info.java", "@NonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(dataFlow.get().getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(new DataFlowInspection(), new ConstantValueInspection());
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testTrueOrEqualsSomething() {
    dataFlow.get().doTest();
    checkIntentionResult("Remove redundant assignment");
  }

  @Test
  public void testDontSimplifyAssignment() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    dataFlow.get().doTest();
    assertThat(myFixture.filterAvailableIntentions("Simplify")).isNullOrEmpty();
  }

  @Test
  public void testVolatileFieldNPEFixes() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    dataFlow.get().doTest();
    assertThat(myFixture.filterAvailableIntentions("Surround")).isNullOrEmpty();
    assertThat(myFixture.filterAvailableIntentions("Assert")).isNullOrEmpty();
    assertThat(myFixture.filterAvailableIntentions("Introduce variable")).isNotEmpty();
  }

  @Test
  public void testConstantConditionsWithAssignmentsInside() { dataFlow.get().doTest(); }
  @Test
  public void testIfConditionsWithAssignmentInside() { dataFlow.get().doTest(); }
  @Test
  public void testBitwiseNegatedBoxed() { dataFlow.get().doTest(); }
  @Test
  public void testDontShadowFinalReassignment() { dataFlow.get().doTest(); }

  @Test
  public void testLiteralIfCondition() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    dataFlow.get().doTest();
    myFixture.findSingleIntention("Remove 'if' statement");
  }

  @Test
  public void testLiteralWhileCondition() {
    dataFlow.get().doTest();
    checkIntentionResult("Remove 'while' statement");
  }

  @Test
  public void testLiteralDoWhileCondition() {
    dataFlow.get().doTest();
    checkIntentionResult("Unwrap 'do-while' statement");
  }
  @Test
  public void testLiteralDoWhileConditionWithBreak() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    dataFlow.get().doTest();
    assertFalse(ContainerUtil.exists(myFixture.getAvailableIntentions(), i -> i.getText().contains("Unwrap 'do-while' statement")));
  }

  @Test
  public void testFalseForConditionNoInitialization() {
    dataFlow.get().doTest();
    checkIntentionResult("Remove 'for' statement");
  }

  @Test
  public void testFalseForConditionWithInitialization() {
    dataFlow.get().doTest();
    checkIntentionResult("Remove 'for' statement");
  }

  @Test
  public void testSideEffectReturn() {
    dataFlow.get().doTest();
    checkIntentionResult("Simplify 'Test.valueOf(...) != null' to true extracting side effects");
  }

  @Test
  public void testSideEffectNoBrace() {
    dataFlow.get().doTest();
    checkIntentionResult("Simplify 'Test.valueOf(...) != null' to true extracting side effects");
  }

  @Test
  public void testSimplifyConcatWithParentheses() {
    dataFlow.get().doTest();
    checkIntentionResult("Simplify 'f' to false");
  }

  @Test
  public void testSideEffectWhile() {
    dataFlow.get().doTest();
    checkIntentionResult("Remove 'while' statement extracting side effects");
  }

  @Test
  public void testUsingInterfaceConstant() { dataFlow.get().doTest();}

  //https://youtrack.jetbrains.com/issue/IDEA-162184
  @Test
  public void testNullLiteralAndInferredMethodContract() {
    dataFlow.get().doTest();
  }
  @Test
  public void testNullLiteralArgumentDoesntReportedWhenMethodOnlyThrowAnException() { dataFlow.get().doTest(); }
  @Test
  public void testNullLiteralArgumentValueUsedAsReturnValue() {
    dataFlow.get().doTest();
  }

  @Test
  public void testCapturedWildcardNotNull() { dataFlow.get().doTest(); }

  @Test
  public void testNullableMethodReturningNotNull() { dataFlow.get().doTest(); }

  @Test
  public void testDivisionByZero() {
    dataFlow.get().doTestWith((i, _) -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
  }

  @Test
  public void testFieldUsedBeforeInitialization() { dataFlow.get().doTest(); }

  @Test
  public void testImplicitlyInitializedField() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    ImplicitUsageProvider.EP_NAME.getPoint().registerExtension(new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitlyNotNullInitialized(@NotNull PsiElement element) {
        return element instanceof PsiField && ((PsiField)element).getName().startsWith("field");
      }

      @Override
      public boolean isClassWithCustomizedInitialization(@NotNull PsiElement element) {
        return element instanceof PsiClass && Objects.equals(((PsiClass)element).getName(), "Instrumented");
      }
    }, myFixture.getTestRootDisposable());
    dataFlow.get().doTest();
  }

  @Test
  public void testNoNonSensicalFixesOnCastedNull() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    dataFlow.get().doTest();
    assertThat(ContainerUtil.findAll(myFixture.getAvailableIntentions(), i -> i.getText().contains("null"))).isNullOrEmpty();
  }

  @Test
  public void testEmptySingletonMap() {dataFlow.get().doTest();}
  @Test
  public void testStaticFieldsWithNewObjects() { dataFlow.get().doTest(); }
  @Test
  public void testComplexInitializer() { dataFlow.get().doTest(); }
  @Test
  public void testFieldAssignedNegative() { dataFlow.get().doTest(); }
  @Test
  public void testIteratePositiveCheck() { dataFlow.get().doTest(); }
  @Test
  public void testInnerClass() { dataFlow.get().doTest(); }
  @Test
  public void testCovariantReturn() { dataFlow.get().doTest(); }
  @Test
  public void testArrayInitializerLength() { dataFlow.get().doTest(); }

  @Test
  public void testGetterOfNullableFieldIsNotAnnotated() { dataFlow.get().doTest(); }

  @Test
  public void testGetterOfNullableFieldIsNotNull() { dataFlow.get().doTest(); }

  @Test
  public void testArrayStoreProblems() { dataFlow.get().doTest(); }
  @Test
  public void testArrayAccessInTry() { dataFlow.get().doTest(); }

  @Test
  public void testNestedScopeComplexity() { dataFlow.get().doTest(); }

  @Test
  public void testNullableReturn() { dataFlow.get().doTest(); }
  @Test
  public void testManyBooleans() { dataFlow.get().doTest(); }
  @Test
  public void testPureNoArgMethodAsVariable() { dataFlow.get().doTest(); }
  @Test
  public void testRedundantAssignment() {
    dataFlow.get().doTest();
    UiInterceptors.register(new ChooserInterceptor(List.of("Extract side effect", "Delete assignment completely"), "Extract side effect"));
    checkIntentionResult("Remove redundant assignment");
  }
  @Test
  public void testXorNullity() { dataFlow.get().doTest(); }
  @Test
  public void testPrimitiveNull() { dataFlow.get().doTest(); }
  @Test
  public void testLessThanRelations() { dataFlow.get().doTest(); }
  @Test
  public void testAdvancedArrayAccess() { dataFlow.get().doTest(); }
  @Test
  public void testNullableGetterInLoop() { dataFlow.get().doTest(); }
  @Test
  public void testNullabilityBasics() { dataFlow.get().doTest(); }
  @Test
  public void testReassignedVarInLoop() { dataFlow.get().doTest(); }
  @Test
  public void testLoopDoubleComparisonNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testAssumeNotNull() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package org.junit; public class Assert { public static void assertTrue(boolean b) {}}");
    myFixture.addClass("package org.junit; public class Assume { public static void assumeNotNull(Object... objects) {}}");
    dataFlow.get().doTest();
  }
  @Test
  public void testMergedInitializerAndConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testClassMethodsInlining() { dataFlow.get().doTest(); }
  @Test
  public void testObjectLocality() { dataFlow.get().doTest(); }
  @Test
  public void testInstanceOfForUnknownVariable() { dataFlow.get().doTest(); }
  @Test
  public void testNanComparisonWrong() { dataFlow.get().doTest(); }
  @Test
  public void testConstantMethods() { dataFlow.get().doTest(); }
  @Test
  public void testPolyadicEquality() { dataFlow.get().doTest(); }
  @Test
  public void testEqualsInLoopNotTooComplex() { dataFlow.get().doTest(); }
  @Test
  public void testEqualsWithItself() { dataFlow.get().doTest(); }
  @Test
  public void testBoxingBoolean() {
    dataFlow.get().doTestWith((_, i) -> i.REPORT_CONSTANT_REFERENCE_VALUES = true);
  }
  @Test
  public void testOrWithAssignment() { dataFlow.get().doTest(); }
  @Test
  public void testAndAndLastOperand() { dataFlow.get().doTest(); }
  @Test
  public void testReportAlwaysNull() {
    dataFlow.get().doTestWith((_, i) -> i.REPORT_CONSTANT_REFERENCE_VALUES = true);
  }

  @Test
  public void testBoxUnboxArrayElement() { dataFlow.get().doTest(); }
  @Test
  public void testExactInstanceOf() { dataFlow.get().doTest(); }
  @Test
  public void testNullFlushed() { dataFlow.get().doTest(); }
  @Test
  public void testBooleanMergeInLoop() { dataFlow.get().doTest(); }
  @Test
  public void testVoidIsAlwaysNull() { dataFlow.get().doTest(); }
  @Test
  public void testImpossibleType() { dataFlow.get().doTest(); }
  @Test
  public void testStringEquality() { dataFlow.get().doTest(); }
  @Test
  public void testStringEqualityNewStringInMethod() { dataFlow.get().doTest(); }
  @Test
  public void testAssignmentFieldAliasing() { dataFlow.get().doTest(); }
  @Test
  public void testNewBoxedNumberEquality() { dataFlow.get().doTest(); }
  @Test
  public void testBoxingIncorrectLiteral() { dataFlow.get().doTest(); }
  @Test
  public void testImplicitUnboxingOnCast() { dataFlow.get().doTest(); }
  @Test
  public void testImplicitUnboxingExtendsInteger() { dataFlow.get().doTest(); }

  @Test
  public void testIncompleteArrayAccessInLoop() { dataFlow.get().doTest(); }
  @Test
  public void testMaxLoop() { dataFlow.get().doTest(); }
  @Test
  public void testExplicitBoxing() { dataFlow.get().doTest(); }
  @Test
  public void testBoxedBoolean() { dataFlow.get().doTest(); }
  @Test
  public void testRedundantSimplifyToFalseQuickFix() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    dataFlow.get().doTest();
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    assertEquals(1, intentions.stream().filter(i -> i.getText().equals("Remove 'if' statement")).count());
    assertEquals(0, intentions.stream().filter(i -> i.getText().equals("Simplify 'expirationDay != other.expirationDay' to false")).count());
  }
  @Test
  public void testAlwaysTrueSwitchLabel() { dataFlow.get().doTest(); }
  @Test
  public void testWideningToDouble() { dataFlow.get().doTest(); }
  @Test
  public void testCompoundAssignment() { dataFlow.get().doTest(); }
  @Test
  public void testNumericCast() { dataFlow.get().doTest(); }
  @Test
  public void testEnumValues() { dataFlow.get().doTest(); }
  @Test
  public void testAssertNullEphemeral() { dataFlow.get().doTest(); }
  @Test
  public void testNotNullAnonymousConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testCaughtNPE() { dataFlow.get().doTest(); }
  @Test
  public void testTernaryNullability() { dataFlow.get().doTest(); }
  @Test
  public void testRewriteFinal() { dataFlow.get().doTest(); }
  @Test
  public void testFinalGettersForFinalFields() { dataFlow.get().doTest(); }
  @Test
  public void testInlineSimpleMethods() { dataFlow.get().doTest(); }
  @Test
  public void testInferenceForNonStableParameters() { dataFlow.get().doTest(); }
  @Test
  public void testNullableTernaryInConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testEqualityLongInteger() { dataFlow.get().doTest(); }
  @Test
  public void testFieldRewrittenInInner() { dataFlow.get().doTest(); }
  @Test
  public void testArrayElementLocality() { dataFlow.get().doTest(); }
  @Test
  public void testOverwrittenParameter() { dataFlow.get().doTest(); }
  @Test
  public void testClassCastExceptionDispatch() { dataFlow.get().doTest(); }
  @Test
  public void testInstanceQualifiedStaticMember() { dataFlow.get().doTest(); }
  @Test
  public void testClassEqualityCornerCase() { dataFlow.get().doTest(); }
  @Test
  public void testCellsComplex() { dataFlow.get().doTest(); }
  @Test
  public void testArraysAsList() { dataFlow.get().doTest(); }
  @Test
  public void testArraycopy() { dataFlow.get().doTest(); }
  @Test
  public void testEnumName() { dataFlow.get().doTest(); }
  @Test
  public void testStaticConstantType() { dataFlow.get().doTest(); }
  @Test
  public void testReassignedAfterNullCheck() { dataFlow.get().doTest(); }
  @Test
  public void testCompareEqualObjectWithNull() { dataFlow.get().doTest(); }
  @Test
  public void testNullabilityAfterCastAndInstanceOf() { dataFlow.get().doTest(); }
  @Test
  public void testInstanceOfTernary() { dataFlow.get().doTest(); }
  @Test
  public void testStringContains() { dataFlow.get().doTest(); }
  @Test
  public void testSwitchLabelNull() { dataFlow.get().doTest(); }
  @Test
  public void testMutationContractInFlush() { dataFlow.get().doTest(); }
  @Test
  public void testMutationContractFromSource() { dataFlow.get().doTest(); }
  @Test
  public void testDefaultConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testInstanceOfUnresolved() { dataFlow.get().doTest(); }
  @Test
  public void testProtobufNotNullGetters() { dataFlow.get().doTest(); }
  @Test
  public void testAIOOBETransfer() { dataFlow.get().doTest(); }
  @Test
  public void testBoxingShortByte() { dataFlow.get().doTest(); }
  @Test
  public void testBoxingIncrement() { dataFlow.get().doTest(); }
  @Test
  public void testUnboxingWithConversionCalls() { dataFlow.get().doTest(); }
  @Test
  public void testNullableAliasing() { dataFlow.get().doTest(); }
  @Test
  public void testReapplyTypeArguments() { dataFlow.get().doTest(); }
  @Test
  public void testDoubleArrayDiff() { dataFlow.get().doTest(); }
  @Test
  public void testInferenceInPrivateOrLocalClass() { dataFlow.get().doTest(); }
  @Test
  public void testArraysCopyOf() { dataFlow.get().doTest(); }
  @Test
  public void testArrayNegativeSize() { dataFlow.get().doTest(); }
  @Test
  public void testArrayWriteFlush() { dataFlow.get().doTest(); }
  @Test
  public void testPresizedList() { dataFlow.get().doTest(); }
  @Test
  public void testCollectionToArray() { dataFlow.get().doTest(); }
  @Test
  public void testStringToCharArray() { dataFlow.get().doTest(); }
  @Test
  public void testFinalStaticFields() { dataFlow.get().doTest(); }
  @Test
  public void testReassignInConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testCollectionViewsSize() { dataFlow.get().doTest(); }
  @Test
  public void testFlushedNullableOnUnknownCall() { dataFlow.get().doTest(); }
  @Test
  public void testBoxedDivisionComparison() { dataFlow.get().doTest(); }
  @Test
  public void testUnknownComparedToNullable() { dataFlow.get().doTest(); }
  @Test
  public void testCastInCatch() { dataFlow.get().doTest(); }
  @Test
  public void testFieldUpdateViaSetter() { dataFlow.get().doTest(); }
  @Test
  public void testInitArrayInConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testGetterNullityAfterCheck() { dataFlow.get().doTest(); }
  @Test
  public void testInferenceNullityMismatch() { dataFlow.get().doTestWith((insp, _) -> insp.SUGGEST_NULLABLE_ANNOTATIONS = false); }
  @Test
  public void testFieldInInstanceInitializer() { dataFlow.get().doTest(); }
  @Test
  public void testCallsBeforeFieldInitializing() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_17, () -> dataFlow.get().doTest());
  }
  @Test
  public void testNullableCallWithPrecalculatedValueAndSpecialField() { dataFlow.get().doTest(); }
  @Test
  public void testJoinConstantAndSubtype() { dataFlow.get().doTest(); }
  @Test
  public void testDereferenceInThrowMessage() { dataFlow.get().doTest(); }
  @Test
  public void testArrayInitializerElementRewritten() { dataFlow.get().doTest(); }
  @Test
  public void testFinallyEphemeralNpe() { dataFlow.get().doTest(); }
  @Test
  public void testTypeParameterAsSuperClass() { dataFlow.get().doTest(); }
  @Test
  public void testSuppressConstantBooleans() { dataFlow.get().doTestWith((_, insp) -> insp.REPORT_CONSTANT_REFERENCE_VALUES = true); }
  @Test
  public void testTempVarsInContracts() { dataFlow.get().doTest(); }
  @Test
  public void testNestedUnrolledLoopNotComplex() { dataFlow.get().doTest(); }
  @Test
  public void testEnumOrdinal() { dataFlow.get().doTest(); }
  @Test
  public void testThisInEnumSubclass() { dataFlow.get().doTest(); }
  @Test
  public void testVarargConstructorNoArgs() { dataFlow.get().doTest(); }
  @Test
  public void testStringBuilderLengthReturn() { dataFlow.get().doTest(); }
  @Test
  public void testEqualsTwoFields() { dataFlow.get().doTest();}
  @Test
  public void testPureMethodReadsMutableArray() { dataFlow.get().doTest(); }
  @Test
  public void testBoxingInConstructorArguments() { dataFlow.get().doTest(); }
  @Test
  public void testBoxingInArrayDeclaration() { dataFlow.get().doTest(); }
  @Test
  public void testBoxedBooleanNullableTrue() { dataFlow.get().doTestWith((_, insp) -> insp.REPORT_CONSTANT_REFERENCE_VALUES = true); }
  @Test
  public void testNestedVersusSuper() { dataFlow.get().doTest(); }
  @Test
  public void testChangeFieldUsedInPureMethod() { dataFlow.get().doTest(); }
  @Test
  public void testSuppression() { dataFlow.get().doTest(); }
  @Test
  public void testRewiringSubclassMethod() { dataFlow.get().doTest(); }
  @Test
  public void testTryWithResourcesCloseThrows() { dataFlow.get().doTest(); }
  @Test
  public void testBooleanOrEquals() { dataFlow.get().doTest(); }
  @Test
  public void testDuplicatedByPointlessBooleanInspection() { dataFlow.get().doTest(); }
  @Test
  public void testSystemOutNullSource() { dataFlow.get().doTest(); }
  @Test
  public void testPrimitiveTypeFieldInWrapper() { dataFlow.get().doTest(); }
  @Test
  public void testNullWarningAfterInstanceofCheck() { dataFlow.get().doTest(); }
  @Test
  public void testNullWarningAfterInstanceofAndNullCheck() { dataFlow.get().doTest(); }
  @Test
  public void testInitializedViaSuperCall() { dataFlow.get().doTest(); }
  @Test
  public void testBoxedBooleanMethodWithCast() { dataFlow.get().doTest(); }
  @Test
  public void testAssignAndReturnVolatile() { dataFlow.get().doTest(); }
  @Test
  public void testQualifiedValueFromConstant() { dataFlow.get().doTest();}
  @Test
  public void testFieldAliasing() { dataFlow.get().doTest();}
  @Test
  public void testFieldLocalNoAliasing() { dataFlow.get().doTest();}
  @Test
  public void testIoContracts() { dataFlow.get().doTest(); }
  @Test
  public void testGetTernary() { dataFlow.get().doTest(); }
  @Test
  public void testClosureInConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testCastIntersectionWithSerializable() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_11, () -> dataFlow.get().doTest());
  }

  @Test
  public void testHugeMethodFlow() { dataFlow.get().doTest(); }
  @Test
  public void testPrivateFieldPureMethod() { dataFlow.get().doTest(); }
  @Test
  public void testNullPrivateFieldForeignCall() { dataFlow.get().doTest(); }
}
