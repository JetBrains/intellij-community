class C {
  @Deprecated
  int field;

  @Deprecated
  void C() { }

  @Deprecated
  void method() { }

  static void bar() {
    C c = new C();
    c.field = 1;
    c.method();
  }
}

class AnotherTopLevel {
  static void bar() {
    C c = new C();
    c.field = 1;
    c.method();
  }
}