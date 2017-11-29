// "Replace with 'Objects.requireNonNull(Math.random() > 0.5 ? null : "bar")'" "true"

import java.util.List;
import java.util.Objects;

class MyClass {
  void foo(String str) {}

  void test() {
    foo(Objects.requireNonNull(Math.random() > 0.5 ? null : "bar"));
  }
}