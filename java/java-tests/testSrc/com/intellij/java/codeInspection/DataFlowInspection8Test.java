// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixture.addCheckerAnnotations;
import static com.intellij.java.codeInspection.DataFlowInspectionFixture.addJSpecifyNullMarked;
import static com.intellij.java.codeInspection.DataFlowInspectionFixture.setupAmbiguousAnnotations;
import static com.intellij.java.codeInspection.DataFlowInspectionFixture.setupTypeUseAnnotations;
import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DataFlowInspection8Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8_ANNOTATED);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  @Test
  public void testAnnotatedTypeParameters() { doTestWithCustomAnnotations(); }
  @Test
  public void testReturnNullInLambdaExpression() { dataFlow.get().doTest(); }
  @Test
  public void testReturnNullInLambdaStatement() { dataFlow.get().doTest(); }
  @Test
  public void testUnboxingBoxingInLambdaReturn() { dataFlow.get().doTest(); }
  @Test
  public void testUnboxingInMethodReferences() { dataFlow.get().doTest(); }
  @Test
  public void testMethodReferenceOnNullable() { dataFlow.get().doTest(); }
  @Test
  public void testObjectsNonNullWithUnknownNullable() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    dataFlow.get().doTestWith((insp, _) -> insp.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true);
  }
  @Test
  public void testNullableVoidLambda() { dataFlow.get().doTest(); }
  @Test
  public void testNullableForeachVariable() { doTestWithCustomAnnotations(); }
  @Test
  public void testGenericParameterNullity() { doTestWithCustomAnnotations(); }
  @Test
  public void testMethodReferenceConstantValue() { doTestWithCustomAnnotations(); }
  @Test
  public void testLambdaAutoCloseable() { dataFlow.get().doTest(); }

  @Test
  public void testOptionalOfNullable() { dataFlow.get().doTest(); }
  @Test
  public void testPrimitiveOptional() { dataFlow.get().doTest(); }
  @Test
  public void testOptionalOrElse() { dataFlow.get().doTest(); }
  @Test
  public void testOptionalIntSwitch() { dataFlow.get().doTest(); }
  @Test
  public void testOptionalIsPresent() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean b) {}" +
                       "}");
    myFixture.addClass("package org.testng;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean b) {}" +
                       "}");
    addGuava();
    dataFlow.get().doTest();
  }

  private void addGuava() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("""
                         package com.google.common.base;

                         public interface Supplier<T> { T get();}
                         """);
    myFixture.addClass("""
                         package com.google.common.base;

                         public interface Function<F, T> { T apply(F input);}
                         """);
    myFixture.addClass("""
                         package com.google.common.base;

                         public abstract class Optional<T> {
                           public static <T> Optional<T> absent() {}
                           public static <T> Optional<T> of(T ref) {}
                           public static <T> Optional<T> fromNullable(T ref) {}
                           public abstract T get();
                           public abstract boolean isPresent();
                           public abstract T orNull();
                           public abstract T or(Supplier<? extends T> supplier);
                           public abstract <V> Optional<V> transform(Function<? super T, V> fn);
                           public abstract T or(T val);
                           public abstract java.util.Optional<T> toJavaUtil();
                         }""");
  }

  @Test
  public void testPrimitiveInVoidLambda() { dataFlow.get().doTest(); }
  @Test
  public void testNotNullLambdaParameter() { dataFlow.get().doTest(); }
  @Test
  public void testNotNullOptionalLambdaParameter() { dataFlow.get().doTest(); }

  @Test
  public void testNullArgumentIsFailingMethodCall() {
    dataFlow.get().doTest();
  }

  @Test
  public void testNullArgumentIsNotFailingMethodCall() {
    dataFlow.get().doTest();
  }

  @Test
  public void testNullArgumentButParameterIsReassigned() {
    dataFlow.get().doTest();
  }

  @Test
  public void testNullArgumentIfMethodExecutionFailsAnyway() {
    dataFlow.get().doTest();
  }

  @Test
  public void testNullableArrayComponent() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupCustomAnnotations();
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.IGNORE_ASSERT_STATEMENTS = true;
    ConstantValueInspection cvInspection = new ConstantValueInspection();
    cvInspection.IGNORE_ASSERT_STATEMENTS = true;
    myFixture.enableInspections(inspection, cvInspection);
    myFixture.testHighlighting(true, false, true, dataFlow.get().getTestName(false) + ".java");
  }

  @Test
  public void testDontSuggestToMakeLambdaNullable() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, dataFlow.get().getTestName(false) + ".java");
  }

  @Test
  public void testLambdaParametersWithDefaultNullability() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testNonNullWhenUnknown() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testReturningNullFromTypeAnnotatedNullableMethod() {
    doTestWithCustomAnnotations();
  }

  private void doTestWithCustomAnnotations() {
    setupCustomAnnotations();
    dataFlow.get().doTest();
  }

  private void setupCustomAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("foo", myFixture);
  }

  @Test
  public void testCapturedWildcardNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testVarargNotNull() { doTestWithCustomAnnotations(); }
  @Test
  public void testIgnoreNullabilityOnPrimitiveCast() { doTestWithCustomAnnotations();}
  @Test
  public void testTypeUseLambdaReturn() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("ambiguous", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testTypeUseInferenceInsideRequireNotNull() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testArrayComponentAndMethodAnnotationConflict() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupAmbiguousAnnotations("withTypeUse", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testTypeUseAmbiguousArrayReturn() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupAmbiguousAnnotations("ambiguous", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testLambdaInlining() { dataFlow.get().doTest(); }

  @Test
  public void testOptionalInlining() {
    addGuava();
    dataFlow.get().doTest();
  }
  @Test
  public void testStreamInlining() { dataFlow.get().doTest(); }
  @Test
  public void testStreamCollectInlining() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("foo", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testStreamCollectorInlining() { dataFlow.get().doTest(); }
  @Test
  public void testStreamToMapInlining() { dataFlow.get().doTest(); }
  @Test
  public void testStreamToMapInlining2() { dataFlow.get().doTest(); }
  @Test
  public void testStreamToCollectionInlining() { dataFlow.get().doTest(); }
  @Test
  public void testStreamComparatorInlining() { dataFlow.get().doTest(); }
  @Test
  public void testStreamKnownSource() { dataFlow.get().doTest(); }
  @Test
  public void testStreamTypeAnnoInlining() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("foo", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testStreamFindFirstExpectNotNull() { dataFlow.get().doTest(); }
  @Test
  public void testStreamAnyMatchIsNull() { dataFlow.get().doTest(); }
  @Test
  public void testStreamCustomSumMethod() { dataFlow.get().doTest(); }
  @Test
  public void testStreamReduceLogicalAnd() { dataFlow.get().doTest(); }
  @Test
  public void testStreamSingleElementReduce() { dataFlow.get().doTest(); }
  @Test
  public void testStreamGroupingBy() { dataFlow.get().doTest(); }
  @Test
  public void testStreamNestedIncomplete() { dataFlow.get().doTest(); }
  @Test
  public void testRequireNonNullMethodRef() {
    dataFlow.get().doTestWith((dfa, _) -> dfa.SUGGEST_NULLABLE_ANNOTATIONS = true);
  }

  @Test
  public void testMapGetWithValueNullability() { doTestWithCustomAnnotations(); }
  @Test
  public void testInferNestedForeachNullability() { doTestWithCustomAnnotations(); }

  @Test
  public void testMethodVsExpressionTypeAnnotationConflict() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupAmbiguousAnnotations("withTypeUse", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testCastInstanceOf() { dataFlow.get().doTest(); }

  @Test
  public void testMutabilityBasics() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package org.jetbrains.annotations;public @interface Unmodifiable {}");
    dataFlow.get().doTest();
  }

  @Test
  public void testMutabilityJdk() { dataFlow.get().doTest(); }

  @Test
  public void testPrimitiveGetters() { dataFlow.get().doTest(); }
  @Test
  public void testUnknownOnStack() { dataFlow.get().doTest(); }
  @Test
  public void testMapUpdateInlining() { doTestWithCustomAnnotations(); }
  @Test
  public void testHashMapImplementation() { dataFlow.get().doTest(); }

  @Test
  public void testOptionalTooComplex() { dataFlow.get().doTest(); }

  @Test
  public void testMethodReferenceBoundToNullable() { doTestWithCustomAnnotations(); }
  @Test
  public void testEscapeAnalysis() { dataFlow.get().doTest(); }
  @Test
  public void testEscapeAnalysisLambdaInConstructor() { dataFlow.get().doTest(); }
  @Test
  public void testThisAsVariable() { dataFlow.get().doTest(); }
  @Test
  public void testQueuePeek() { dataFlow.get().doTest(); }
  @Test
  public void testForeachCollectionElement() { dataFlow.get().doTest(); }
  @Test
  public void testContractReturnValues() { dataFlow.get().doTest(); }
  @Test
  public void testTryFinallySimple() { dataFlow.get().doTest(); }
  @Test
  public void testAssertAll() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("""
                         package org.junit.jupiter.api;

                         import org.junit.jupiter.api.function.Executable;

                         public class Assertions {
                           public static void assertAll(String s, Executable... e) {}
                           public static void assertAll(Executable... e) {}
                           public static void assertNotNull(Object o) {}
                           public static void assertTrue(boolean b) {}
                         }""");
    myFixture.addClass("package org.junit.jupiter.api.function;public interface Executable { void execute() throws Throwable;}\n");
    dataFlow.get().doTest();
  }

  @Test
  public void testConflictsInInferredTypes() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupAmbiguousAnnotations("foo", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testObjectsEquals() { dataFlow.get().doTest(); }
  @Test
  public void testManyObjectEquals() { dataFlow.get().doTest(); }
  @Test
  public void testManyObjectEquals2() { dataFlow.get().doTest(); }
  @Test
  public void testLambdaAfterNullCheck() { dataFlow.get().doTest(); }
  @Test
  public void testFlatMapSideEffect() { dataFlow.get().doTest(); }
  @Test
  public void testOptionalValueTracking() { dataFlow.get().doTest(); }
  @Test
  public void testOptionalAsQualifier() { dataFlow.get().doTest(); }
  @Test
  public void testDelegateClassTypeParameter() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testClearZeroesSize() { dataFlow.get().doTest(); }
  @Test
  public void testLambdaInlineReassignReturnWithDeeperEquality() { dataFlow.get().doTest(); }

  @Test
  public void testReturningNonNullFromMethodWithNullableArrayInReturnType() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupAmbiguousAnnotations("mixed", myFixture);
    setupTypeUseAnnotations("typeUse", myFixture);
    NullableNotNullManager.getInstance(myFixture.getProject()).setNullables("mixed.Nullable", "typeUse.Nullable");
    dataFlow.get().doTest();
  }

  @Test
  public void testLambdaWritesArrayInTry() { dataFlow.get().doTest(); }
  @Test
  public void testManyNestedOptionals() { dataFlow.get().doTest(); }
  @Test
  public void testGetClass() { dataFlow.get().doTest(); }
  @Test
  public void testParamContract() { dataFlow.get().doTest(); }
  @Test
  public void testParamContractBoolean() { dataFlow.get().doTest(); }
  @Test
  public void testTypeUseVarArg() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testLambdaReturnFromTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testInlineLambdaFromLocal() { dataFlow.get().doTest(); }
  @Test
  public void testAllowRequireNonNullInCtor() { dataFlow.get().doTest(); }
  @Test
  public void testNullableNotNullAssignmentInReturn() { dataFlow.get().doTest(); }
  @Test
  public void testTransformMethod() { dataFlow.get().doTest(); }
  @Test
  public void testTernaryExpressionNumericType() { dataFlow.get().doTest(); }
  @Test
  public void testEclipseDefaultTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package org.eclipse.jdt.annotation;public @interface NonNullByDefault {}");
    dataFlow.get().doTest();
  }
  @Test
  public void testEclipseDefaultOptionalOrElse() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("package org.eclipse.jdt.annotation;public @interface NonNullByDefault {}");
    myFixture.addClass("package org.eclipse.jdt.annotation;import java.lang.annotation.*;" +
                       "@Target({ElementType.TYPE_USE}) public @interface Nullable {}");
    dataFlow.get().doTest();
  }
  @Test
  public void testClassInsideLambda() { dataFlow.get().doTest(); }
  @Test
  public void testMultiDimensionalArrays() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testImplicitUnboxingInMethodReference() {
    dataFlow.get().doTest();
  }
  @Test
  public void testArrayTypeParameterInference() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testArrayTypeParameterInferenceAmbiguous() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupAmbiguousAnnotations("ambiguous", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testGuavaFunction() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testMethodReferenceNullableToNotNull() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testModifyListInLambda() {
    dataFlow.get().doTest();
  }
  @Test
  public void testConstantInClosure() { dataFlow.get().doTest(); }
  @Test
  public void testUnknownNullability() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         import java.lang.annotation.*;
                         @Target(ElementType.TYPE_USE)
                         public @interface UnknownNullability { }""");
    dataFlow.get().doTestWith((insp, _) -> insp.SUGGEST_NULLABLE_ANNOTATIONS = false);
  }
  @Test
  public void testReturnOrElseNull() { dataFlow.get().doTestWith((insp, _) -> insp.REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = true); }
  @Test
  public void testArrayIntersectionType() { dataFlow.get().doTest(); }
  @Test
  public void testFunctionType() { dataFlow.get().doTest(); }
  @Test
  public void testIteratorHasNextModifiesPrivateField() { dataFlow.get().doTest(); }
  @Test
  public void testJsr305TypeUseNoLocal() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testDefaultAnnotationForLoopParameter() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testCheckerDefaultQualifier() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addCheckerAnnotations(myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testSpotBugsDefaultAnnotation() {
    dataFlow.get().doTest();
  }
  @Test
  public void testConstructorMethodReferenceNullability() { dataFlow.get().doTest(); }
  @Test
  public void testCustomStreamImplementation() { dataFlow.get().doTest(); }
  @Test
  public void testEmptyCollection() { dataFlow.get().doTest(); }
  @Test
  public void testConsumedStream() { dataFlow.get().doTest(); }
  @Test
  public void testConsumedStreamDifferentMethods() { dataFlow.get().doTest(); }
  @Test
  public void testConsumedStreamWithoutInline()  { dataFlow.get().doTest(); }
  @Test
  public void testLocalityAndConditionalExpression() { dataFlow.get().doTest(); }
  @Test
  public void testParallelStreamThreadId() { dataFlow.get().doTest(); }
  @Test
  public void testCompletableFutureWhenComplete() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    dataFlow.get().doTest();
  }
  @Test
  public void testFieldWriteInLambda() { dataFlow.get().doTest(); }
  @Test
  public void testNewMethodReferenceMustBeNonNull() {
    dataFlow.get().doTestWith((insp, _) -> insp.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true);
  }
  @Test
  public void testExternalTypeParameterAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }
}