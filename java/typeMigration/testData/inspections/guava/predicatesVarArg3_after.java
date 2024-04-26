package org.example;

import java.util.function.Predicate;

public class Main {
  void foo(Predicate<String> p0, Predicate<Integer> p1) {
    call(10, p0.negate(), p1.negate());
  }

  public static void call(int x, Predicate<String>, Predicate<Integer>, Predicate<?>... pr) {
  }
}