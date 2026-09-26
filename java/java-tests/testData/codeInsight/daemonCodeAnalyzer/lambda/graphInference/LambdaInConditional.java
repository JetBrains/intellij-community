interface I {
  void f();
}

class B implements I {
  @Override
  public void f() {

  }
}

class MyTest {
  void m(I i) {}

  void n(int ik) {
    m(ik > 0 ? () -> {} : new B());
  }
}