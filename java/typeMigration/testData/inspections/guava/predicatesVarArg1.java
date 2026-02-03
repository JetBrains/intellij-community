package org.example;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class Main {
  void foo(Predicate<String> p0, Predicate<Integer> p1) {
    call(Predicates.not(p0), Predicates.not(p1));
  }
  public static <T> void call(java.util.function.Predicate<?>... pr) {
  }

}