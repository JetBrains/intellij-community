package org.example;

import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class ConstructorTest {
  interface NullableExpectedLib<K extends @Nullable Object> {
  }

  static class NullableLibExpectedInArguments {
    NullableLibExpectedInArguments(NullableExpectedLib<@Nullable Object> l) {
    }
  }

  void test(NullableExpectedLib<Object> l) {
    new NullableLibExpectedInArguments(<warning descr="Assigning a class with not-null type arguments when a class with nullable type arguments is expected">l</warning>);
  }

  static class NullableLibExpectedInArguments2<T extends @Nullable Object> {
    NullableLibExpectedInArguments2(NullableExpectedLib<T> l) {
    }
  }

  void test2(NullableExpectedLib<Object> l) {
    new NullableLibExpectedInArguments2<>(l);
  }
}
