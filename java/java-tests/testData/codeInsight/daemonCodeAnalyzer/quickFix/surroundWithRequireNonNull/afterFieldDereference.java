// "Replace with 'Objects.requireNonNull(getObject())'" "true-preview"

import java.util.Objects;

class MyClass {
  int a;

  static MyClass getObject() {
    return Math.random() > 0.5 ? new MyClass() : null;
  }
  void test() {
    Objects.requireNonNull(getObject()).a = 5;
  }
}