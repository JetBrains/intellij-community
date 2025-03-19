package com.example;

import org.jspecify.annotations.NullMarked;
import org.jetbrains.annotations.Nullable;

@NullMarked
final class JSpecifyLocal<T> {
  void test() {
    T s = getSomething();
  }

  native @Nullable T getSomething();
}
