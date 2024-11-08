// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspection8Test extends DataFlowInspectionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testAnnotatedTypeParameters() { doTestWithCustomAnnotations(); }
  public void testReturnNullInLambdaExpression() { doTest(); }
  public void testReturnNullInLambdaStatement() { doTest(); }
  public void testUnboxingBoxingInLambdaReturn() { doTest(); }
  public void testUnboxingInMethodReferences() { doTest(); }
  public void testMethodReferenceOnNullable() { doTest(); }
  public void testObjectsNonNullWithUnknownNullable() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTestWith((insp, __) -> insp.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true);
  }
  public void testNullableVoidLambda() { doTest(); }
  public void testNullableForeachVariable() { doTestWithCustomAnnotations(); }
  public void testGenericParameterNullity() { doTestWithCustomAnnotations(); }
  public void testMethodReferenceConstantValue() { doTestWithCustomAnnotations(); }
  public void testLambdaAutoCloseable() { doTest(); }

  public void testOptionalOfNullable() { doTest(); }
  public void testPrimitiveOptional() { doTest(); }
  public void testOptionalOrElse() { doTest(); }
  public void testOptionalIntSwitch() { doTest(); }
  public void testOptionalIsPresent() {
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean b) {}" +
                       "}");
    myFixture.addClass("package org.testng;" +
                       "public class Assert {" +
                       "  public static void assertTrue(boolean b) {}" +
                       "}");
    addGuava();
    doTest();
  }

  private void addGuava() {
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

  public void testPrimitiveInVoidLambda() { doTest(); }
  public void testNotNullLambdaParameter() { doTest(); }
  public void testNotNullOptionalLambdaParameter() { doTest(); }

  public void testNullArgumentIsFailingMethodCall() {
    doTest();
  }

  public void testNullArgumentIsNotFailingMethodCall() {
    doTest();
  }

  public void testNullArgumentButParameterIsReassigned() {
    doTest();
  }

  public void testNullArgumentIfMethodExecutionFailsAnyway() {
    doTest();
  }

  public void testNullableArrayComponent() {
    setupCustomAnnotations();
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.IGNORE_ASSERT_STATEMENTS = true;
    ConstantValueInspection cvInspection = new ConstantValueInspection();
    cvInspection.IGNORE_ASSERT_STATEMENTS = true;
    myFixture.enableInspections(inspection, cvInspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testDontSuggestToMakeLambdaNullable() {
    DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testLambdaParametersWithDefaultNullability() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  public void testNonNullWhenUnknown() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  public void testReturningNullFromTypeAnnotatedNullableMethod() {
    doTestWithCustomAnnotations();
  }

  private void doTestWithCustomAnnotations() {
    setupCustomAnnotations();
    doTest();
  }

  private void setupCustomAnnotations() {
    setupTypeUseAnnotations("foo", myFixture);
  }

  public void testCapturedWildcardNotNull() { doTest(); }
  public void testVarargNotNull() { doTestWithCustomAnnotations(); }
  public void testIgnoreNullabilityOnPrimitiveCast() { doTestWithCustomAnnotations();}
  public void testTypeUseLambdaReturn() {
    setupTypeUseAnnotations("ambiguous", myFixture);
    doTest();
  }
  public void testTypeUseInferenceInsideRequireNotNull() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testArrayComponentAndMethodAnnotationConflict() {
    setupAmbiguousAnnotations("withTypeUse", myFixture);
    doTest();
  }

  public void testTypeUseAmbiguousArrayReturn() {
    setupAmbiguousAnnotations("ambiguous", myFixture);
    doTest();
  }
  public void testLambdaInlining() { doTest(); }

  public void testOptionalInlining() {
    addGuava();
    doTest();
  }
  public void testStreamInlining() { doTest(); }
  public void testStreamCollectInlining() {
    setupTypeUseAnnotations("foo", myFixture);
    doTest();
  }
  public void testStreamCollectorInlining() { doTest(); }
  public void testStreamToMapInlining() { doTest(); }
  public void testStreamToMapInlining2() { doTest(); }
  public void testStreamToCollectionInlining() { doTest(); }
  public void testStreamComparatorInlining() { doTest(); }
  public void testStreamKnownSource() { doTest(); }
  public void testStreamTypeAnnoInlining() {
    setupTypeUseAnnotations("foo", myFixture);
    doTest();
  }
  public void testStreamFindFirstExpectNotNull() { doTest(); }
  public void testStreamAnyMatchIsNull() { doTest(); }
  public void testStreamCustomSumMethod() { doTest(); }
  public void testStreamReduceLogicalAnd() { doTest(); }
  public void testStreamSingleElementReduce() { doTest(); }
  public void testStreamGroupingBy() { doTest(); }
  public void testStreamNestedIncomplete() { doTest(); }
  public void testRequireNonNullMethodRef() {
    doTestWith((dfa, __) -> dfa.SUGGEST_NULLABLE_ANNOTATIONS = true);
  }

  public void testMapGetWithValueNullability() { doTestWithCustomAnnotations(); }
  public void testInferNestedForeachNullability() { doTestWithCustomAnnotations(); }

  public void testMethodVsExpressionTypeAnnotationConflict() {
    setupAmbiguousAnnotations("withTypeUse", myFixture);
    doTest();
  }

  public void testCastInstanceOf() { doTest(); }

  public void testMutabilityBasics() {
    myFixture.addClass("package org.jetbrains.annotations;public @interface Unmodifiable {}");
    doTest();
  }

  public void testMutabilityJdk() { doTest(); }

  public void testPrimitiveGetters() { doTest(); }
  public void testUnknownOnStack() { doTest(); }
  public void testMapUpdateInlining() { doTestWithCustomAnnotations(); }
  public void testHashMapImplementation() { doTest(); }

  public void testOptionalTooComplex() { doTest(); }

  public void testMethodReferenceBoundToNullable() { doTestWithCustomAnnotations(); }
  public void testEscapeAnalysis() { doTest(); }
  public void testEscapeAnalysisLambdaInConstructor() { doTest(); }
  public void testThisAsVariable() { doTest(); }
  public void testQueuePeek() { doTest(); }
  public void testForeachCollectionElement() { doTest(); }
  public void testContractReturnValues() { doTest(); }
  public void testTryFinallySimple() { doTest(); }
  public void testAssertAll() {
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
    doTest();
  }

  public void testConflictsInInferredTypes() {
    setupAmbiguousAnnotations("foo", myFixture);
    doTest();
  }
  public void testObjectsEquals() { doTest(); }
  public void testManyObjectEquals() { doTest(); }
  public void testManyObjectEquals2() { doTest(); }
  public void testLambdaAfterNullCheck() { doTest(); }
  public void testFlatMapSideEffect() { doTest(); }
  public void testOptionalValueTracking() { doTest(); }
  public void testOptionalAsQualifier() { doTest(); }
  public void testClearZeroesSize() { doTest(); }
  public void testLambdaInlineReassignReturnWithDeeperEquality() { doTest(); }

  public void testReturningNonNullFromMethodWithNullableArrayInReturnType() {
    setupAmbiguousAnnotations("mixed", myFixture);
    setupTypeUseAnnotations("typeUse", myFixture);
    NullableNotNullManager.getInstance(getProject()).setNullables("mixed.Nullable", "typeUse.Nullable");
    doTest();
  }

  public void testLambdaWritesArrayInTry() { doTest(); }
  public void testManyNestedOptionals() { doTest(); }
  public void testGetClass() { doTest(); }
  public void testParamContract() { doTest(); }
  public void testParamContractBoolean() { doTest(); }
  public void testTypeUseVarArg() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  public void testLambdaReturnFromTypeUse() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  public void testInlineLambdaFromLocal() { doTest(); }
  public void testAllowRequireNonNullInCtor() { doTest(); }
  public void testNullableNotNullAssignmentInReturn() { doTest(); }
  public void testTransformMethod() { doTest(); }
  public void testTernaryExpressionNumericType() { doTest(); }
  public void testEclipseDefaultTypeUse() {
    myFixture.addClass("package org.eclipse.jdt.annotation;public @interface NonNullByDefault {}");
    doTest();
  }
  public void testEclipseDefaultOptionalOrElse() {
    myFixture.addClass("package org.eclipse.jdt.annotation;public @interface NonNullByDefault {}");
    myFixture.addClass("package org.eclipse.jdt.annotation;import java.lang.annotation.*;" +
                       "@Target({ElementType.TYPE_USE}) public @interface Nullable {}");
    doTest();
  }
  public void testClassInsideLambda() { doTest(); }
  public void testMultiDimensionalArrays() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  public void testImplicitUnboxingInMethodReference() {
    doTest();
  }
  public void testArrayTypeParameterInference() {
    setupTypeUseAnnotations("typeUse", myFixture);
    NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
    nnnManager.setDefaultNotNull("typeUse.NotNull");
    nnnManager.setDefaultNullable("typeUse.Nullable");
    Disposer.register(getTestRootDisposable(), () -> {
      nnnManager.setDefaultNotNull(AnnotationUtil.NOT_NULL);
      nnnManager.setDefaultNullable(AnnotationUtil.NULLABLE);
    });
    doTest();
  }
  public void testArrayTypeParameterInferenceAmbiguous() {
    setupAmbiguousAnnotations("ambiguous", myFixture);
    NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
    nnnManager.setDefaultNotNull("ambiguous.NotNull");
    nnnManager.setDefaultNullable("ambiguous.Nullable");
    Disposer.register(getTestRootDisposable(), () -> {
      nnnManager.setDefaultNotNull(AnnotationUtil.NOT_NULL);
      nnnManager.setDefaultNullable(AnnotationUtil.NULLABLE);
    });
    doTest();
  }
  public void testGuavaFunction() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  public void testMethodReferenceNullableToNotNull() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  public void testModifyListInLambda() {
    doTest();
  }
  public void testConstantInClosure() { doTest(); }
  public void testUnknownNullability() {
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         import java.lang.annotation.*;
                         @Target(ElementType.TYPE_USE)
                         public @interface UnknownNullability { }""");
    doTestWith((insp, __) -> insp.SUGGEST_NULLABLE_ANNOTATIONS = false);
  }
  public void testReturnOrElseNull() { doTestWith((insp, __) -> insp.REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = true); }
  public void testArrayIntersectionType() { doTest(); }
  public void testFunctionType() { doTest(); }
  public void testIteratorHasNextModifiesPrivateField() { doTest(); }
  public void testJsr305TypeUseNoLocal() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }
  public void testDefaultAnnotationForLoopParameter() {
    setupTypeUseAnnotations("typeUse", myFixture);
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    doTest();
  }
  public void testCheckerDefaultQualifier() {
    addCheckerAnnotations(myFixture);
    doTest();
  }
  public void testSpotBugsDefaultAnnotation() {
    doTest();
  }
  public void testConstructorMethodReferenceNullability() { doTest(); }
  public void testCustomStreamImplementation() { doTest(); }
  public void testEmptyCollection() { doTest(); }
  public void testConsumedStream() { doTest(); }
  public void testConsumedStreamDifferentMethods() { doTest(); }
  public void testConsumedStreamWithoutInline()  { doTest(); }
  public void testLocalityAndConditionalExpression() { doTest(); }
  public void testParallelStreamThreadId() { doTest(); }
  public void testCompletableFutureWhenComplete() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
}