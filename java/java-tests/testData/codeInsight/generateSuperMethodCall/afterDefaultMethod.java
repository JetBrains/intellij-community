interface I {
  default void f() {}
}

interface A extends I {
  @Override
  default void f() {
      I.super.f();

  }
}