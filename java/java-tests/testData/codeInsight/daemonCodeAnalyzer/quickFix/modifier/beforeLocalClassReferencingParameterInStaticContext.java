// "Make 'p' static" "false"

class A {
  void m(int p) {
    class C {
      static int P = <caret>p;
    }
  }
}
