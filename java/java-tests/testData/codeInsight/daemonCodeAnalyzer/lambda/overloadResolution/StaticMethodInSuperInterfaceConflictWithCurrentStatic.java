interface A {
  static void m() {}
}

interface B extends A {
  static void m() {}

  default void f() {
    m();
  }
}
