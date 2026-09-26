// "Replace with 'Objects.requireNonNull(Math.random() > 0.5 ? get(1) : get(2))'" "true-preview"

import java.util.List;

class MyClass {
  void foo(String str) {}

  void test() {
    foo(Math.random() > 0.5 ? get(1) :<caret> get(2));
  }
  
  static String get(int x) {
    return x > 0 ? "foo" : null;
  }
}