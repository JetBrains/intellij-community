
interface I {
  static void f<caret>oo() {}
}

class C implements I { }

class Usage {
  void bar() {
    I.foo();
  }
}