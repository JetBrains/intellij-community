// "Make 'X.foo' not abstract" "true"

abstract class X {
  abstract void foo();
}

class Y extends X {
  void foo() {
    super.foo()<caret>;
  }
}