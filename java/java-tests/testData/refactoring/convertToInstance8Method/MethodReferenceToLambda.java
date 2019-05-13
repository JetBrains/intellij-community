class Bar0 {}
class Bar {
  void f() {
    I r = Bar::foo;
  }

  private static void fo<caret>o(Integer i, Bar0 bar) { }
}
interface I {
  void m(Integer i, Bar0 b);
}