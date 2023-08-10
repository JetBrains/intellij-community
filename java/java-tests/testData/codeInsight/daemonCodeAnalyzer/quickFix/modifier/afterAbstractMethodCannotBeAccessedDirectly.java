// "Make 'X.foo()' not abstract" "true-preview"

abstract class X {
    void foo() {
        <caret>
    }
}

class Y extends X {
  void foo() {
    super.foo();
  }
}