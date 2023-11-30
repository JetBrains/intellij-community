package org.example;

import java.util.function.Predicate;

public class Main {
  void foo(Predicate<String> p0, Predicate<Integer> p1) {
    call(p0.negate(), p1.negate());
  }
  public static <T> void call(Predicate<?>... pr) {
  }

}