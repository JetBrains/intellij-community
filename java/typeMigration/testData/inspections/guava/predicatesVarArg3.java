package org.example;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class Main {
  void foo(Predicate<String> p0, Predicate<Integer> p1) {
    call(10, Predicates.not(p0), Predicates.not(p1));
  }

  public static void call(int x, java.util.function.Predicate<String>, java.util.function.Predicate<Integer>, java.util.function.Predicate<?>... pr) {
  }
}