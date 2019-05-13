interface I {
  static void f<caret>oo(I i) {}
}

class WithPrivateInner {
  private class Inner implements I {}
}

class WithUsage {
  void n(I i) {
    I.foo(i);
  }
}