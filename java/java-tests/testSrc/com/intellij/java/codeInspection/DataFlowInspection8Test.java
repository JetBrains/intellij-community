// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
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
    myFixture.addClass("package com.google.common.base;\n" +
                       "\n" +
                       "public interface Supplier<T> { T get();}\n");
    myFixture.addClass("package com.google.common.base;\n" +
                       "\n" +
                       "public interface Function<F, T> { T apply(F input);}\n");
    myFixture.addClass("package com.google.common.base;\n" +
                       "\n" +
                       "public abstract class Optional<T> {\n" +
                       "  public static <T> Optional<T> absent() {}\n" +
                       "  public static <T> Optional<T> of(T ref) {}\n" +
                       "  public static <T> Optional<T> fromNullable(T ref) {}\n" +
                       "  public abstract T get();\n" +
                       "  public abstract boolean isPresent();\n" +
                       "  public abstract T orNull();\n" +
                       "  public abstract T or(Supplier<? extends T> supplier);\n" +
                       "  public abstract <V> Optional<V> transform(Function<? super T, V> fn);\n" +
                       "  public abstract T or(T val);\n" +
                       "  public abstract java.util.Optional<T> toJavaUtil();\n" +
                       "}");
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
    myFixture.enableInspections(inspection);
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

  static void setupTypeUseAnnotations(String pkg, JavaCodeInsightTestFixture fixture) {
    setupCustomAnnotations(pkg, "{ElementType.TYPE_USE}", fixture);
  }

  private static void setupCustomAnnotations(String pkg, String target, JavaCodeInsightTestFixture fixture) {
    fixture.addClass("package " + pkg + ";\n\nimport java.lang.annotation.*;\n\n@Target(" + target + ") public @interface Nullable { }");
    fixture.addClass("package " + pkg + ";\n\nimport java.lang.annotation.*;\n\n@Target(" + target + ") public @interface NotNull { }");
    setCustomAnnotations(fixture.getProject(), fixture.getTestRootDisposable(), pkg + ".NotNull", pkg + ".Nullable");
  }

  static void setCustomAnnotations(Project project, Disposable parentDisposable, String notNull, String nullable) {
    NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(project);
    nnnManager.setNotNulls(notNull);
    nnnManager.setNullables(nullable);
    Disposer.register(parentDisposable, () -> {
      nnnManager.setNotNulls();
      nnnManager.setNullables();
    });
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

  static void setupAmbiguousAnnotations(String pkg, JavaCodeInsightTestFixture fixture) {
    setupCustomAnnotations(pkg, "{ElementType.METHOD, ElementType.TYPE_USE}", fixture);
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
  public void testStreamAnyMatchIsNull() { doTest(); }
  public void testStreamCustomSumMethod() { doTest(); }
  public void testStreamReduceLogicalAnd() { doTest(); }
  public void testStreamSingleElementReduce() { doTest(); }

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
    myFixture.addClass("package org.junit.jupiter.api;\n" +
                       "\n" +
                       "import org.junit.jupiter.api.function.Executable;\n" +
                       "\n" +
                       "public class Assertions {\n" +
                       "  public static void assertAll(String s, Executable... e) {}\n" +
                       "  public static void assertAll(Executable... e) {}\n" +
                       "  public static void assertNotNull(Object o) {}\n" +
                       "  public static void assertTrue(boolean b) {}\n" +
                       "}");
    myFixture.addClass("package org.junit.jupiter.api.function;public interface Executable { void execute() throws Throwable;}\n");
    doTest();
  }

  public void testConflictsInInferredTypes() {
    setupAmbiguousAnnotations("foo", myFixture);
    doTest();
  }
  public void testObjectsEquals() { doTest(); }
  public void testManyObjectEquals() { doTest(); }
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
    myFixture.addClass("package org.jetbrains.annotations;\nimport java.lang.annotation.*;\n" +
                       "@Target(ElementType.TYPE_USE)\n" +
                       "public @interface UnknownNullability { }");
    doTestWith(insp -> insp.SUGGEST_NULLABLE_ANNOTATIONS = false);
  }
  public void testReturnOrElseNull() { doTestWith(insp -> insp.REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = true); }
  public void testArrayIntersectionType() { doTest(); }
  public void testFunctionType() { doTest(); }
  public void testJsr305TypeUseNoLocal() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }
}