// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * @author peter
 */
public class DataFlowInspectionTest extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7_ANNOTATED;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testTryInAnonymous() { doTest(); }
  public void testNullableAnonymousMethod() { doTest(); }
  public void testNullableAnonymousParameter() { doTest(); }
  public void testNullableAnonymousVolatile() { doTest(); }
  public void testNullableAnonymousVolatileNotNull() { doTest(); }
  public void testLocalClass() { doTest(); }

  public void testNotNullOnSuperParameter() { doTest(); }
  public void testNullableOnSuperParameter() { doTest(); }

  public void testFieldInAnonymous() { doTest(); }
  public void testFieldInitializerInAnonymous() { doTest(); }
  public void testNullableField() { doTest(); }
  public void testCanBeNullDoesntImplyIsNull() { doTest(); }
  public void testAnnReport() { doTest(); }

  public void testSuppressStaticFlags() { doTest(); }

  public void testBigMethodNotComplex() { doTest(); }
  public void testBuildRegexpNotComplex() { doTest(); }
  public void testTernaryInWhileNotComplex() { doTest(); }
  public void testTryCatchInForNotComplex() { doTest(); }
  public void testTryReturnCatchInWhileNotComplex() { doTest(); }
  public void testNestedTryInWhileNotComplex() { doTest(); }
  public void testExceptionFromFinally() { doTest(); }
  public void testExceptionFromFinallyNesting() { doTest(); }
  public void testNestedFinally() { doTest(); }
  public void testTryFinallyInsideFinally() { doTest(); }
  public void testBreakContinueViaFinally() { doTest(); }
  public void testFieldChangedBetweenSynchronizedBlocks() { doTest(); }

  public void testGeneratedEquals() { doTest(); }

  public void testIDEA84489() { doTest(); }
  public void testComparingNullToNotNull() { doTest(); }
  public void testComparingNullableToNullable() { doTest(); }
  public void testComparingNullableToUnknown() { doTest(); }
  public void testComparingToNotNullShouldNotAffectNullity() { doTest(); }
  public void testComparingToNullableShouldNotAffectNullity() { doTest(); }
  public void testStringTernaryAlwaysTrue() { doTest(); }
  public void testStringConcatAlwaysNotNull() { doTest(); }

  public void testNotNullPrimitive() { doTest(); }
  public void testBoxing128() { doTest(); }
  public void testFinalFieldsInitializedByAnnotatedParameters() { doTest(); }
  public void testFinalFieldsInitializedNotNull() { doTest(); }
  public void testMultiCatch() { doTest(); }
  public void testContinueFlushesLoopVariable() { doTest(); }

  public void testEqualsNotNull() { doTest(); }
  public void testVisitFinallyOnce() { doTest(); }
  public void testNotEqualsDoesntImplyNotNullity() { doTest(); }
  public void testEqualsEnumConstant() { doTest(); }
  public void testSwitchEnumConstant() { doTest(); }
  public void testEphemeralDefaultCaseVisited() { doTest(); }
  public void testEphemeralInIfChain() { doTest(); }
  public void testIncompleteSwitchEnum() { doTest(); }
  public void testEnumConstantNotNull() { doTest(); }
  public void testCheckEnumConstantConstructor() { doTest(); }
  public void testCompareToEnumConstant() { doTest(); }
  public void testEqualsConstant() { doTest(); }
  public void testInternedStringConstants() { doTest(); }
  public void testDontSaveTypeValue() { doTest(); }
  public void testFinalLoopVariableInstanceof() { doTest(); }
  public void testGreaterIsNotEquals() { doTest(); }
  public void testNotGreaterIsNotEquals() { doTest(); }

  public void testAnnotationMethodNotNull() { doTest(); }

  public void testChainedFinalFieldsDfa() { doTest(); }
  public void testFinalFieldsDifferentInstances() { doTest(); }
  public void testThisFieldGetters() { doTest(); }
  public void testChainedFinalFieldAccessorsDfa() { doTest(); }
  public void testAccessorPlusMutator() { doTest(); }
  public void testClosureVariableField() { doTest(); }
  public void testOptionalThis() { doTest(); }
  public void testQualifiedThis() { doTest(); }

  public void testAssigningNullableToNotNull() { doTest(); }
  public void testAssigningUnknownToNullable() { doTest(); }
  public void testAssigningClassLiteralToNullable() { doTest(); }

  public void testSynchronizingOnNullable() { doTest(); }
  public void testSwitchOnNullable() { doTest(); }
  public void testReturningNullFromVoidMethod() { doTest(); }
  public void testReturningNullConstant() { doTest(); }
  public void testReturningConstantExpression() { doTest(); }

  public void testCatchRuntimeException() { doTest(); }
  // IDEA-129331
  //public void testCatchThrowable() throws Throwable { doTest(); }
  public void testNotNullCatchParameter() { doTest(); }

  public void testAssertFailInCatch() {
    myFixture.addClass("package org.junit; public class Assert { public static void fail() {}}");
    doTest();
  }

  public void testPreserveNullableOnUncheckedCast() { doTest(); }
  public void testPrimitiveCastMayChangeValue() { doTest(); }

  public void testPassingNullableIntoVararg() { doTest(); }
  public void testEqualsImpliesNotNull() {
    doTestWith(i -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
  }
  public void testEffectivelyUnqualified() { doTest(); }

  public void testQualifierEquality() { doTest(); }

  public void testSkipAssertions() {
    doTestWith(i -> {
      i.DONT_REPORT_TRUE_ASSERT_STATEMENTS = true;
      i.REPORT_CONSTANT_REFERENCE_VALUES = true;
    });
  }

  public void testParanoidMode() {
    doTestWith(i -> i.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true);
  }

  public void testReportConstantReferences() {
    doTestWith(i -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
    String hint = "Replace with 'null'";
    checkIntentionResult(hint);
  }

  private void checkIntentionResult(String hint) {
    myFixture.launchAction(myFixture.findSingleIntention(hint));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
    PsiTestUtil.checkPsiMatchesTextIgnoringNonCode(getFile());
  }

  public void testReportConstantReferences_OverloadedCall() {
    doTestWith(i -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
    checkIntentionResult("Replace with 'null'");
  }

  public void testReportConstantReferencesAfterFinalFieldAccess() {
    doTestWith(i -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
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
    doTestWith(i -> {
      i.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true;
      i.REPORT_CONSTANT_REFERENCE_VALUES = false;
    });
  }


  public void _testSymmetricUncheckedCast() { doTest(); } // https://youtrack.jetbrains.com/issue/IDEABKL-6871
  public void testNullCheckDoesntAffectUncheckedCast() { doTest(); }
  public void testThrowNull() { doTest(); }
  public void testThrowNullable() { doTest(); }

  public void testExplicitlyNullableLocalVar() { doTest(); }

  public void testTryWithResourcesNullability() { doTest(); }
  public void testTryWithResourcesInstanceOf() { doTest(); }
  public void testTryWithResourcesCloseException() { doTest(); }
  public void testTryWithResourceExpressions() { doTest(); }

  public void testOmnipresentExceptions() { doTest(); }

  public void testEqualsHasNoSideEffects() { doTest(); }

  public void testHonorGetterAnnotation() { doTest(); }

  public void testIgnoreAssertions() {
    doTestWith(i -> i.IGNORE_ASSERT_STATEMENTS = true);
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

  public void testKeepComments() {
    doTest();
    checkIntentionResult("Simplify");
  }

  public void testImmutableClassNonGetterMethod() {
    myFixture.addClass("package com.google.auto.value; public @interface AutoValue {}");
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
    doTestWith(i -> i.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true);
  }

  public void testTypeQualifierNickname() {
    addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass(barNullableNick());

    doTest();
  }

  public void testTypeQualifierNicknameWithoutDeclarations() {
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

  public void testSideEffectReturn() {
    doTest();
    checkIntentionResult("Simplify 'Test.valueOf(...) != null' to true extracting side effects");
  }

  public void testSideEffectNoBrace() {
    doTest();
    checkIntentionResult("Simplify 'Test.valueOf(...) != null' to true extracting side effects");
  }

  public void testSimplifyConcatWithParentheses() {
    doTest();
    checkIntentionResult("Simplify 'f' to false");
  }

  public void testSideEffectWhile() {
    doTest();
    checkIntentionResult("Remove 'while' statement extracting side effects");
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

  public void testDivisionByZero() {
    doTestWith(i -> i.SUGGEST_NULLABLE_ANNOTATIONS = true);
  }

  public void testFieldUsedBeforeInitialization() { doTest(); }

  public void testImplicitlyInitializedField() {
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
    doTest();
  }

  public void testNoNonSensicalFixesOnCastedNull() {
    doTest();
    assertEmpty(ContainerUtil.findAll(myFixture.getAvailableIntentions(), i -> i.getText().contains("null")));
  }

  public void testEmptySingletonMap() {doTest();}
  public void testStaticFieldsWithNewObjects() { doTest(); }
  public void testComplexInitializer() { doTest(); }
  public void testFieldAssignedNegative() { doTest(); }
  public void testIteratePositiveCheck() { doTest(); }
  public void testInnerClass() { doTest(); }
  public void testCovariantReturn() { doTest(); }
  public void testArrayInitializerLength() { doTest(); }

  public void testGetterOfNullableFieldIsNotAnnotated() { doTest(); }

  public void testGetterOfNullableFieldIsNotNull() { doTest(); }

  public void testArrayStoreProblems() { doTest(); }

  public void testNestedScopeComplexity() { doTest(); }

  public void testNullableReturn() { doTest(); }
  public void testManyBooleans() { doTest(); }
  public void testPureNoArgMethodAsVariable() { doTest(); }
  public void testRedundantAssignment() {
    doTest();
    assertIntentionAvailable("Extract side effect");
  }
  public void testXorNullity() { doTest(); }
  public void testPrimitiveNull() { doTest(); }
  public void testLessThanRelations() { doTest(); }
  public void testAdvancedArrayAccess() { doTest(); }
  public void testNullableGetterInLoop() { doTest(); }
  public void testNullabilityBasics() { doTest(); }
  public void testReassignedVarInLoop() { doTest(); }
  public void testLoopDoubleComparisonNotComplex() { doTest(); }
  public void testAssumeNotNull() {
    myFixture.addClass("package org.junit; public class Assert { public static void assertTrue(boolean b) {}}");
    myFixture.addClass("package org.junit; public class Assume { public static void assumeNotNull(Object... objects) {}}");
    doTest();
  }
  public void testMergedInitializerAndConstructor() { doTest(); }
  public void testClassMethodsInlining() { doTest(); }
  public void testObjectLocality() { doTest(); }
  public void testInstanceOfForUnknownVariable() { doTest(); }
  public void testNanComparisonWrong() { doTest(); }
  public void testConstantMethods() { doTest(); }
  public void testPolyadicEquality() { doTest(); }
  public void testEqualsInLoopNotTooComplex() { doTest(); }
  public void testEqualsWithItself() { doTest(); }
  public void testBoxingBoolean() {
    doTestWith(i -> i.REPORT_CONSTANT_REFERENCE_VALUES = true);
  }
  public void testOrWithAssignment() { doTest(); }
  public void testAndAndLastOperand() { doTest(); }
  public void testReportAlwaysNull() {
    doTestWith(i -> i.REPORT_CONSTANT_REFERENCE_VALUES = true);
  }

  public void testBoxUnboxArrayElement() { doTest(); }
  public void testExactInstanceOf() { doTest(); }
  public void testNullFlushed() { doTest(); }
  public void testBooleanMergeInLoop() { doTest(); }
  public void testVoidIsAlwaysNull() { doTest(); }
  public void testImpossibleType() { doTest(); }
  public void testStringEquality() { doTest(); }
  public void testStringEqualityNewStringInMethod() { doTest(); }
  public void testAssignmentFieldAliasing() { doTest(); }
  public void testNewBoxedNumberEquality() { doTest(); }
  public void testBoxingIncorrectLiteral() { doTest(); }
  public void testImplicitUnboxingOnCast() { doTest(); }
  public void testImplicitUnboxingExtendsInteger() { doTest(); }

  public void testIncompleteArrayAccessInLoop() { doTest(); }
  public void testSameArguments() { doTest(); }
  public void testMaxLoop() { doTest(); }
  public void testExplicitBoxing() { doTest(); }
  public void testBoxedBoolean() { doTest(); }
  public void testRedundantSimplifyToFalseQuickFix() {
    doTest();
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    assertEquals(1, intentions.stream().filter(i -> i.getText().equals("Remove 'if' statement")).count());
    assertEquals(0, intentions.stream().filter(i -> i.getText().equals("Simplify 'expirationDay != other.expirationDay' to false")).count());
  }
  public void testAlwaysTrueSwitchLabel() { doTest(); }
  public void testWideningToDouble() { doTest(); }
  public void testCompoundAssignment() { doTest(); }
  public void testNumericCast() { doTest(); }
  public void testEnumValues() { doTest(); }
  public void testEmptyCollection() { doTest(); }
  public void testAssertNullEphemeral() { doTest(); }
  public void testNotNullAnonymousConstructor() { doTest(); }
  public void testCaughtNPE() { doTest(); }
  public void testTernaryNullability() { doTest(); }
  public void testRewriteFinal() { doTest(); }
  public void testFinalGettersForFinalFields() { doTest(); }
  public void testInlineSimpleMethods() { doTest(); }
  public void testInferenceForNonStableParameters() { doTest(); }
  public void testNullableTernaryInConstructor() { doTest(); }
  public void testEqualityLongInteger() { doTest(); }
  public void testFieldRewrittenInInner() { doTest(); }
  public void testArrayElementLocality() { doTest(); }
  public void testOverwrittenParameter() { doTest(); }
  public void testClassCastExceptionDispatch() { doTest(); }
  public void testInstanceQualifiedStaticMember() { doTest(); }
  public void testClassEqualityCornerCase() { doTest(); }
  public void testCellsComplex() { doTest(); }
  public void testArraysAsList() { doTest(); }
  public void testArraycopy() { doTest(); }
  public void testEnumName() { doTest(); }
  public void testStaticConstantType() { doTest(); }
  public void testReassignedAfterNullCheck() { doTest(); }
  public void testCompareEqualObjectWithNull() { doTest(); }
  public void testNullabilityAfterCastAndInstanceOf() { doTest(); }
  public void testInstanceOfTernary() { doTest(); }
  public void testStringContains() { doTest(); }
  public void testSwitchLabelNull() { doTest(); }
  public void testMutationContractInFlush() { doTest(); }
  public void testMutationContractFromSource() { doTest(); }
  public void testDefaultConstructor() { doTest(); }
  public void testInstanceOfUnresolved() { doTest(); }
  public void testProtobufNotNullGetters() { doTest(); }
  public void testAIOOBETransfer() { doTest(); }
  public void testBoxingShortByte() { doTest(); }
  public void testBoxingIncrement() { doTest(); }
  public void testUnboxingWithConversionCalls() { doTest(); }
  public void testNullableAliasing() { doTest(); }
  public void testReapplyTypeArguments() { doTest(); }
  public void testDoubleArrayDiff() { doTest(); }
  public void testInferenceInPrivateOrLocalClass() { doTest(); }
  public void testArraysCopyOf() { doTest(); }
  public void testArrayNegativeSize() { doTest(); }
  public void testPresizedList() { doTest(); }
  public void testCollectionToArray() { doTest(); }
  public void testStringToCharArray() { doTest(); }
  public void testFinalStaticFields() { doTest(); }
  public void testReassignInConstructor() { doTest(); }
  public void testCollectionViewsSize() { doTest(); }
  public void testFlushedNullableOnUnknownCall() { doTest(); }
  public void testBoxedDivisionComparison() { doTest(); }
  public void testUnknownComparedToNullable() { doTest(); }
  public void testCastInCatch() { doTest(); }
  public void testInitArrayInConstructor() { doTest(); }
  public void testGetterNullityAfterCheck() { doTest(); }
  public void testInferenceNullityMismatch() { doTestWith(insp -> insp.SUGGEST_NULLABLE_ANNOTATIONS = false); }
  public void testFieldInInstanceInitializer() { doTest(); }
  public void testNullableCallWithPrecalculatedValueAndSpecialField() { doTest(); }
  public void testJoinConstantAndSubtype() { doTest(); }
  public void testDereferenceInThrowMessage() { doTest(); }
  public void testArrayInitializerElementRewritten() { doTest(); }
  public void testFinallyEphemeralNpe() { doTest(); }
  public void testTypeParameterAsSuperClass() { doTest(); }
  public void testSuppressConstantBooleans() { doTestWith(insp -> insp.REPORT_CONSTANT_REFERENCE_VALUES = true); }
  public void testTempVarsInContracts() { doTest(); }
  public void testNestedUnrolledLoopNotComplex() { doTest(); }
  public void testEnumOrdinal() { doTest(); }
  public void testThisInEnumSubclass() { doTest(); }
  public void testVarargConstructorNoArgs() { doTest(); }
  public void testStringBuilderLengthReturn() { doTest(); }
  public void testEqualsTwoFields() { doTest();}
  public void testPureMethodReadsMutableArray() { doTest(); }
  public void testBoxingInConstructorArguments() { doTest(); }
}
