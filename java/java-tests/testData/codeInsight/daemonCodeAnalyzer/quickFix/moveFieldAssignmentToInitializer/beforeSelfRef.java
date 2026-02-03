// "Move assignment to field declaration" "false"

class Foo {
  int[] s;

  void foo() {
    if (s != null) {
      s = s.cl<caret>one();
    }
  }
}