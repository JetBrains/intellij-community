package org.example;

import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

class DefaultNotNullTypeParameterOverrides {

  static void callNullable(Lib<? extends @Nullable Object> l) {
  }

  static void callNonnull(Lib<? extends @NotNull Object> l) {
  }

  static void callSuperNullable(Lib<? super @Nullable Object> l) {
  }

  static void callSuperNonnull(Lib<? super @NotNull Object> l) {
  }

  static void simple(Lib<@Nullable Object> nullable,
                     Lib<@NotNull Object> notnull,
                     Lib<? extends @Nullable Object> extNullable,
                     Lib<? extends @NotNull Object> extNotNullable) {

    callNullable(nullable);
    callNullable(notnull);
    callNullable(extNullable);
    callNullable(extNotNullable);

    callNonnull(<warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">nullable</warning>);
    callNonnull(notnull);
    callNonnull(<warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">extNullable</warning>);
    callNonnull(extNotNullable);

    callSuperNullable(nullable);
    callSuperNullable(<warning descr="Incompatible type arguments due to nullability">notnull</warning>);

    callSuperNonnull(nullable);
    callSuperNonnull(notnull);
  }

  static void callLibNullable(Lib<Lib<? extends @Nullable Object>> l) {
  }

  static void callLibNonnull(Lib<Lib<? extends @NotNull Object>> l) {
  }

  static void nested(Lib<Lib<? extends @Nullable Object>> extNullable,
                     Lib<Lib<? extends @NotNull Object>> extNotNullable) {

    callLibNullable(extNullable);
    callLibNullable(<warning descr="Assigning a class with not-null type arguments when a class with nullable type arguments is expected">extNotNullable</warning>);

    callLibNonnull(<warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">extNullable</warning>);
    callLibNonnull(extNotNullable);
  }


  interface SuperInt<T extends @Nullable Object> {
    @NullMarked
    void nonNull(Lib<? extends Object> lib);

    Lib<@Nullable T> nullReturn();
  }

  @NullMarked
  interface ChildInt<T extends Object & Runnable> extends
                                                  SuperInt<T> {

    default void testWildcard() {
      nonNull(<warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">nullReturn()</warning>);
    }
  }


  @NullMarked
  interface CaptureTest {

    <L> void nonNullExpected(Lib<L> l);

    default void captured(Lib<? extends Object> l) {
      nonNullExpected(l);
    }
  }

  static class Lib<T extends @Nullable Object> {

  }
}

