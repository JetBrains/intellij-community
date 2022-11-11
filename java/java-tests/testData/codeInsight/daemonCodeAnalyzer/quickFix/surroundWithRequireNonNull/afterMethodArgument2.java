// "Replace with 'Objects.requireNonNull(Math.random() > 0.5 ? get(1) : get(2))'" "true-preview"

import java.util.List;
import java.util.Objects;

class MyClass {
  void foo(String str) {}

  void test() {
    foo(Objects.requireNonNull(Math.random() > 0.5 ? get(1) : get(2)));
  }
  
  static String get(int x) {
    return x > 0 ? "foo" : null;
  }
}