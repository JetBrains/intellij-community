// "Make 'X.foo()' not abstract" "true-preview"

abstract class X {
  abstract void foo();
}

class Y extends X {
  void foo() {
    super.foo()<caret>;
  }
}