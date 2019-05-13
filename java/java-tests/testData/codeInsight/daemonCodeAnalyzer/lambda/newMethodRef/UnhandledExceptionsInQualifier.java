class E1 extends Exception {}
class E2 extends Exception {}
class Test {
  interface I {
    void m() throws E1;
  }

  void a(I i) {}
  Test b() throws E2 {return this;}
  void c() throws E1 {}
  void e() throws E1, E2 {}

  void d() throws E2 {
    a(b()::c);
    a(<error descr="Unhandled exception: E2">this::e</error>);
  }
}