class Foo {
  static int n;
  static {
    <warning descr="The value 1 assigned to 'Foo.n' is never used">Foo.n</warning> = 1;
    <warning descr="The value 2 assigned to 'n' is never used">n</warning> = 2;
    n = 3;
  }

  float f;
  {
    <warning descr="The value 1.1f assigned to 'this.f' is never used">this.f</warning> = 1.1f;
    <warning descr="The value 2.2f assigned to 'f' is never used">f</warning> = 2.2f;
    f = 3.3f;
  }

  String s;
  Foo() {
    <warning descr="The value \"a\" assigned to 'this.s' is never used">this.s</warning> = "a";
    <warning descr="The value \"b\" assigned to 's' is never used">s</warning> = "b";
    s = "c";
  }

  static void foo() {
    n = 4; // no warning
    n = 5;
  }

  void bar() {
    f = 4.4f; // no warning
    f = 5.5f;
  }
}