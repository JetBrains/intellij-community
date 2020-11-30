package com.google.common.base;

import typeUse.*;

interface Function<F, T> extends java.util.function.Function<F, T> {
  @Override
  @Nullable
  T apply(@Nullable F input);
}

interface Predicate<T> extends java.util.function.Predicate<T> {
  boolean apply(@Nullable T input);
  default boolean test(@Nullable T input) {
    return apply(input);
  }
}

class Test {
  void test(Predicate<String> pred) {
    pred.apply(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
  }
  void testNullable(Predicate<@Nullable String> pred) {
    pred.apply(null);
  }
  void testNotNull(Predicate<@NotNull String> pred) {
    pred.apply(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }
  void apply(Function<String, String> fn) {
    fn.apply(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>).hashCode();
  }
  void applyNullable(Function<@Nullable String, @Nullable String> fn) {
    fn.apply(null).<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>();
  }
  void applyNotNull(Function<@NotNull String, @NotNull String> fn) {
    fn.apply(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>).hashCode();
  }
  
  void call() {
    test(x -> x.hashCode() > 0);
    testNullable(x -> x.<warning descr="Method invocation 'hashCode' may produce 'NullPointerException'">hashCode</warning>() > 0);
    testNotNull(x -> x.hashCode() > 0);
    apply(x -> x.trim());
    applyNullable(x -> x.<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
    applyNotNull(x -> x.trim());
  }
}