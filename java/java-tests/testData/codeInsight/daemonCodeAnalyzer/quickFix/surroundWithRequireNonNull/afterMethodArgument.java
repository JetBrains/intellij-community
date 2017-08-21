// "Replace with 'Objects.requireNonNull(arr)'" "true"

import java.util.Objects;

class MyClass {
  void foo(String[] arr) {}

  void test() {
    String[] arr = Math.random() > 0.5 ? null : new String[10];
    foo(Objects.requireNonNull(arr));
  }
}