// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.nullaway;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;

import static com.intellij.java.impl.nullaway.NullAwayProblem.Kind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class NullAwayProblemTest {

  @ParameterizedTest
  @MethodSource
  void filePathParsedCorrectly(String logLine, String expectedFilePath) {
    NullAwayProblem problem = NullAwayProblem.fromLogLine(logLine);
    assertEquals(Path.of(expectedFilePath), problem.filePath());
  }

  public static List<Arguments> filePathParsedCorrectly() {
    return List.of(
      // Gradle format
      Arguments.of(
        "/full/path/MyClass.java:10: error: [NullAway] @NonNull field myValue not initialized",
        "/full/path/MyClass.java"),
      Arguments.of(
        "/full/path/AClass.java:11: warning: [NullAway] @NonNull field someValue not initialized",
        "/full/path/AClass.java"),
      // Maven format
      Arguments.of(
        "/full/path/MyClass.java:[100,11] [NullAway] @NonNull field value3 not initialized",
        "/full/path/MyClass.java"),
      Arguments.of(
        "[WARNING] /full/path/AClass.java:[102,11] [NullAway] @NonNull field valueX not initialized",
        "/full/path/AClass.java")
    );
  }

  @ParameterizedTest
  @MethodSource
  void lineNumberParsedCorrectly(String logLine, int expectedLineNumber) {
    NullAwayProblem problem = NullAwayProblem.fromLogLine(logLine);
    assertEquals(expectedLineNumber, problem.lineNumber());
  }

  public static List<Arguments> lineNumberParsedCorrectly() {
    return List.of(
      // gradle
      Arguments.of("/full/path/MyClass.java:10: error: [NullAway] @NonNull field myValue not initialized", 9),
      Arguments.of("/full/path/MyClass.java:10:12: error: [NullAway] @NonNull field myValue not initialized", 9),
      // maven
      Arguments.of("/full/path/MyClass.java:[100,11] [NullAway] @NonNull field value3 not initialized", 99),
      Arguments.of("/full/path/MyClass.java:[101] [NullAway] @NonNull field value3 not initialized", 100),
      Arguments.of("[WARNING] /full/path/AClass.java:[102,11] [NullAway] @NonNull field valueX not initialized", 101)
    );
  }

  @ParameterizedTest
  @MethodSource
  void kindMatchedCorrectly(String logLine, NullAwayProblem.Kind expectedKind) {
    NullAwayProblem problem = NullAwayProblem.fromLogLine(logLine);
    assertEquals(expectedKind, problem.kind());
  }

  public static List<Arguments> kindMatchedCorrectly() {
    return List.of(
      Arguments.of(
        "/full/path/MyClass.java:10: error: [NullAway] @NonNull field myValue not initialized",
        NON_NULL_FIELD_NOT_INITIALIZED),
      Arguments.of(
        "/full/path/MyClass.java:20: error: [NullAway] initializer method does not guarantee @NonNull field fieldA (line 33) is initialized along all control-flow paths (remember to check for exceptions or early returns).",
        INITIALIZER_DOES_NOT_GUARANTEE_INITIALIZATION),
      Arguments.of(
        "/full/path/MyClass.java:30: error: [NullAway] dereferenced expression data is @Nullable",
        DEREFERENCED_EXPRESSION_IS_NULLABLE),
      Arguments.of(
        "/full/path/MyClass.java:40: error: [NullAway] returning @Nullable expression from method with @NonNull return type",
        RETURNING_NULLABLE_FROM_NONNULL_METHOD),
      Arguments.of(
        "/full/path/MyClass.java:50: error: [NullAway] passing @Nullable parameter 'foo' where @NonNull is required",
        PASSING_NULLABLE_PARAMETER_WHERE_NONNULL_REQUIRED),
      Arguments.of(
        "/full/path/MyClass.java:60: error: [NullAway] method returns @Nullable, but superclass method org.testcases.SuperClass.getstring() returns @NonNull",
        METHOD_RETURNS_NULLABLE_BUT_SUPERCLASS_METHOD_NONNULL),
      Arguments.of(
        "/full/path/MyClass.java:70: error: [NullAway] assigning @Nullable expression to @NonNull field",
        ASSIGNING_NULLABLE_TO_NONNULL_FIELD),
      Arguments.of(
        "/full/path/MyClass.java:80: error: [NullAway] referenced method returns @Nullable, but functional interface method someMethod() returns @NonNull",
        REFERENCED_METHOD_RETURNS_NULLABLE),
      Arguments.of(
        "/full/path/MyClass.java:90: error: [NullAway] unbound instance method reference cannot be used, as first parameter param1 is @Nullable",
        UNBOUND_INSTANCE_METHOD_REFERENCE_FIRST_PARAMETER_NULLABLE),
      Arguments.of(
        "/full/path/MyClass.java:100: error: [NullAway] parameter param1 is @NonNull, but parameter in superMethod is @Nullable",
        PARAMETER_IS_NONNULL_BUT_PARAMETER_IN_SUPERCLASS_NULLABLE),
      Arguments.of(
        "/full/path/MyClass.java:110: error: [NullAway] unboxing of a @Nullable value",
        UNBOXING_OF_NULLABLE_VALUE),
      Arguments.of(
        "/full/path/MyClass.java:120: error: [NullAway] read of @NonNull field myField before initialization",
        READ_OF_NONNULL_FIELD_BEFORE_INIT),
      Arguments.of(
        "/full/path/MyClass.java:130: error: [NullAway] enhanced-for expression items is @Nullable",
        ENHANCED_FOR_EXPRESSION_NULLABLE),
      Arguments.of(
        "/full/path/MyClass.java:140: error: [NullAway] synchronized block expression \"lock\" is @Nullable",
        SYNCHRONIZED_BLOCK_EXPRESSION_NULLABLE),
      Arguments.of(
        "/full/path/MyClass.java:150: error: [NullAway] @NonNull static field myStaticField not initialized",
        NONNULL_STATIC_FIELD_NOT_INITIALIZED),
      Arguments.of(
        "/full/path/MyClass.java:160: error: [NullAway] passing known @NonNull parameter 'value' to CastToNonNullMethod (com.example.Util.cast) is unnecessary",
        PASSING_NONNULL_TO_CAST_TO_NONNULL),
      Arguments.of(
        "/full/path/MyClass.java:170: error: [NullAway] Invoking get() on possibly empty Optional myOptional",
        INVOKING_GET_ON_EMPTY_OPTIONAL),
      Arguments.of(
        "/full/path/MyClass.java:180: error: [NullAway] switch expression result is @Nullable",
        SWITCH_EXPRESSION_NULLABLE),
      Arguments.of(
        "/full/path/MyClass.java:190: error: [NullAway] Method is annotated with @EnsuresNonNull but fails to ensure field myField is non-null",
        METHOD_ANNOTATED_WITH_ENSURES_NONNULL_BUT_FAILS),
      Arguments.of(
        "/full/path/MyClass.java:200: error: [NullAway] Method is annotated with @EnsuresNonNullIf but does not ensure fields myField are non-null",
        METHOD_ANNOTATED_WITH_ENSURES_NONNULL_IF_BUT_DOES_NOT_ENSURE),
      Arguments.of(
        "/full/path/MyClass.java:210: error: [NullAway] Expected static field myStaticField to be non-null at call site due to @RequiresNonNull annotation on invoked method",
        EXPECTED_STATIC_FIELD_NONNULL_DUE_TO_REQUIRES_NONNULL),
      Arguments.of(
        "/full/path/MyClass.java:220: error: [NullAway] Expected field myField to be non-null at call site due to @RequiresNonNull annotation on invoked method",
        EXPECTED_FIELD_NONNULL_DUE_TO_REQUIRES_NONNULL),
      Arguments.of(
        "/full/path/MyClass.java:230: error: [NullAway] postcondition inheritance is violated, this method must guarantee that all fields myField are @NonNull after invocation",
        POSTCONDITION_INHERITANCE_VIOLATED),
      Arguments.of(
        "/full/path/MyClass.java:240: error: [NullAway] precondition inheritance is violated, method in child class cannot have a stricter precondition than parent",
        PRECONDITION_INHERITANCE_VIOLATED),
      Arguments.of(
        "/full/path/MyClass.java:250: error: [NullAway] Type argument cannot be @Nullable, as method someMethod's type variable T is not @Nullable",
        TYPE_ARGUMENT_CANNOT_BE_NULLABLE),
      Arguments.of(
        "/full/path/MyClass.java:260: error: [NullAway] Generic type parameter cannot be @Nullable, as type variable T of type MyClass does not have a @Nullable upper bound",
        GENERIC_TYPE_PARAMETER_CANNOT_BE_NULLABLE),
      Arguments.of(
        "/full/path/MyClass.java:270: error: [NullAway] incompatible types: List<@Nullable String> cannot be converted to List<String>",
        INCOMPATIBLE_TYPES_GENERIC),
      Arguments.of(
        "/full/path/MyClass.java:280: error: [NullAway] Conditional expression must have type List<String> but the sub-expression has type List<@Nullable String>",
        CONDITIONAL_EXPRESSION_TYPE_MISMATCH),
      Arguments.of(
        "/full/path/MyClass.java:290: error: [NullAway] Method returns List<@Nullable String>, but overridden method returns List<String>, which has mismatched type parameter nullability",
        METHOD_RETURNS_GENERIC_WITH_MISMATCHED_NULLABILITY),
      Arguments.of(
        "/full/path/MyClass.java:300: error: [NullAway] Parameter has type List<String>, but overridden method has parameter type List<@Nullable String>, which has mismatched type parameter nullability",
        PARAMETER_TYPE_GENERIC_WITH_MISMATCHED_NULLABILITY),
      Arguments.of(
        "/full/path/MyClass.java:310: error: [NullAway] Writing @Nullable expression into array with @NonNull contents.",
        WRITING_NULLABLE_INTO_NONNULL_ARRAY),
      Arguments.of(
        "/full/path/MyClass.java:320: error: [NullAway] Failed to infer type argument nullability for call someMethod()",
        FAILED_TO_INFER_TYPE_ARGUMENT_NULLABILITY),
      Arguments.of(
        "/full/path/MyClass.java:330: error: [NullAway] Type-use nullability annotations should be applied on inner class",
        TYPE_USE_NULLABILITY_ON_WRONG_NESTED_CLASS_LEVEL)
    );
  }
}
