class Bar0 {}
class Bar {
  void f() {
    I r = Bar::foo;
  }

  private static void fo<caret>o(Bar0 bar) { }
}
interface I {
  void m(Bar0 b);
}