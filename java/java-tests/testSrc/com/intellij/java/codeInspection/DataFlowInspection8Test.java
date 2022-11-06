// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
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
  public void testStreamFindFirstExpectNotNull() { doTest(); }
  public void testStreamAnyMatchIsNull() { doTest(); }
  public void testStreamCustomSumMethod() { doTest(); }
  public void testStreamReduceLogicalAnd() { doTest(); }
  public void testStreamSingleElementReduce() { doTest(); }
  public void testStreamGroupingBy() { doTest(); }
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
  public void testConstructorMethodReferenceNullability() { doTest(); }
  public void testCustomStreamImplementation() { doTest(); }
  public void testEmptyCollection() { doTest(); }
  public void testConsumedStream() { doTest(); }
  public void testConsumedStreamDifferentMethods() { doTest(); }
  public void testConsumedStreamWithoutInline()  { doTest(); }
  @SuppressWarnings({"override", "MethodOverloadsMethodOfSuperclass"})
  public void testDateTimeTracking()  {
    myFixture.addClass("""
                         package java.time.chrono;
                         public interface ChronoLocalDate { }""");
    myFixture.addClass("""
                         package java.time.chrono;
                         public interface ChronoLocalDateTime<T> { }""");
    myFixture.addClass("""
                         package java.time.chrono;
                         public interface ChronoZonedDateTime<T> {
                           default boolean isBefore(ChronoZonedDateTime<?> offsetTime2) { return false; }
                           default boolean isAfter(ChronoZonedDateTime<?> offsetTime2) { return false; }
                           default boolean isEqual(ChronoZonedDateTime<?> offsetTime2) { return false; }
                         }""");
    myFixture.addClass("""
                         package java.time;
                         public enum ZoneOffset {UTC}""");
    myFixture.addClass("""
                         package java.time;
                         public enum Month {JANUARY, FEBRUARY, MARCH, APRIL}""");
    myFixture.addClass("""
                         package java.time.temporal;
                         public interface TemporalUnit{}""");
    myFixture.addClass("""
                         package java.time.temporal;
                         public public enum ChronoUnit implements TemporalUnit{NANOS, MICROS, MILLIS, SECONDS, MINUTES, HOURS, HALF_DAYS,
                         DAYS, WEEKS, MONTHS, YEARS}""");
    myFixture.addClass("""
                         package java.time;
                         import java.util.Random;
                         public final class OffsetDateTime {
                           public static OffsetDateTime of(LocalDate localDate, LocalTime localTime, ZoneOffset zoneOffset) {
                               return new OffsetDateTime();
                           }
                           public boolean isBefore(OffsetDateTime offsetDateTime2) { return new Random().nextBoolean(); }
                           public boolean isEqual(OffsetDateTime offsetDateTime2) { return new Random().nextBoolean(); }
                         }""");
    myFixture.addClass("""
                         package java.time;
                         import java.time.temporal.TemporalUnit;
                         public final class OffsetTime {
                            public static OffsetTime of(LocalTime localTime, ZoneOffset zoneOffset) {
                                return new OffsetTime();
                           }
                           public boolean isBefore(OffsetTime offsetTime2) { return false; }
                           public boolean isAfter(OffsetTime offsetTime2) { return false; }
                           public boolean isEqual(OffsetTime offsetTime2) { return false; }
                           public OffsetTime plusHours(long amountToAdd) { return new OffsetTime(); }
                           public OffsetTime plus(long amountToAdd, TemporalUnit unit) { return new OffsetTime(); }
                         }""");
    myFixture.addClass("""
                         package java.time;
                         import java.time.chrono.ChronoZonedDateTime;
                         public final class ZonedDateTime implements ChronoZonedDateTime<LocalDate> {
                            public static ZonedDateTime of(LocalDateTime localDateTime, ZoneOffset zoneOffset) {
                                return new ZonedDateTime();
                           }
                         }""");

    myFixture.addClass("""
                        package java.time;
                        import java.time.chrono.ChronoLocalDate;
                        import java.time.temporal.TemporalUnit;
                        public final class LocalDate implements ChronoLocalDate {
                          public static LocalDate of(int year, int month, int day) { return new LocalDate(); }
                          public static LocalDate of(int year, Month month, int day) { return new LocalDate(); }
                          public static LocalDate now() { return new LocalDate(); }
                          public static LocalDate ofYearDay(int year, int dayOfYear) { return new LocalDate(); }
                          public boolean isBefore(ChronoLocalDate localDate2) { return true; }
                          public boolean isAfter(ChronoLocalDate localDate2) { return true; }
                          public boolean isEqual(ChronoLocalDate localDate2) { return true; }
                          public boolean equals(Object localDate2) { return true; }
                          public LocalDate plus(long amountToAdd, TemporalUnit unit) { return new LocalDate(); }
                          public LocalDate plusYears(long amountToAdd) { return new LocalDate(); }
                          public LocalDate minusYears(long amountToAdd) { return new LocalDate(); }
                          public int getYear() { return 0; }
                          public int getMonthValue() { return 0; }
                          public LocalDate minus(long amountToAdd, TemporalUnit unit) { return new LocalDate(); }
                         }""");
    myFixture.addClass("""
                        package java.time;
                        import java.time.temporal.TemporalUnit;
                        public final class LocalTime {
                          public static LocalTime of(int hours, int minutes, int seconds) {  return new LocalTime(); }
                          public static LocalTime of(int hours, int minutes, int seconds, int nanos) {  return new LocalTime(); }
                          public static LocalTime now() { return new LocalTime(); }
                          public boolean isBefore(LocalTime localTime) { return false; }
                          public boolean isAfter(LocalTime localTime) { return false; }
                          public boolean equals(Object localTime) { return true; }
                          public LocalTime plus(long amountToAdd, TemporalUnit unit) { return new LocalTime(); }
                          public LocalTime plusHours(long amountToAdd) { return new LocalTime(); }
                          public LocalTime minusHours(long amountToAdd) { return new LocalTime(); }
                          public LocalTime minus(long amountToAdd, TemporalUnit unit) { return new LocalTime(); }
                          public LocalTime withHour(int hour) { return new LocalTime(); }
                          public int getHour() { return 0; }
                         }""");
    myFixture.addClass("""
                        package java.time;
                        import java.time.chrono.ChronoLocalDateTime;
                        import java.time.temporal.TemporalUnit;
                        public final class LocalDateTime implements ChronoLocalDateTime<LocalDate> {
                          public static LocalDateTime now() { return new LocalDateTime(); }
                          public static LocalDateTime of(int years, int month, int day, int hour, int minutes) {
                              return new LocalDateTime();
                          }
                          public static LocalDateTime of(int years, Month month, int day, int hour, int minutes, int seconds) {
                              return new LocalDateTime();
                          }
                          public boolean isBefore(ChronoLocalDateTime<LocalDate> localDateTime2) { return false; }
                          public boolean isAfter(ChronoLocalDateTime<LocalDate> localDateTime2) { return false; }
                          public boolean isEqual(ChronoLocalDateTime<LocalDate> localDateTime2) { return false; }
                          public boolean equals(Object localDateTime2) { return true; }
                          public LocalDateTime plus(long amountToAdd, TemporalUnit unit) { return new LocalDateTime(); }
                          public LocalDateTime minus(long amountToAdd, TemporalUnit unit) { return new LocalDateTime(); }
                          public LocalDateTime plusHours(long hour) { return new LocalDateTime(); }
                          public int getHour() { return 0; }
                         }""");
    doTest(); }
}