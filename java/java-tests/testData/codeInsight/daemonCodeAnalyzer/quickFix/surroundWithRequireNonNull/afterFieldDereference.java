// "Replace with 'Objects.requireNonNull(getObject())'" "true"

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