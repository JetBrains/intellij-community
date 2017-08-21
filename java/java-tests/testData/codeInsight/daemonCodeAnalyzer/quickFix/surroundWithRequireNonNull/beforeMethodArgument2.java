// "Replace with 'Objects.requireNonNull(Math.random() > 0.5 ? null : "bar")'" "true"

import java.util.List;

class MyClass {
  void foo(String str) {}

  void test() {
    foo(Math.random() > 0.5 ? null :<caret> "bar");
  }
}