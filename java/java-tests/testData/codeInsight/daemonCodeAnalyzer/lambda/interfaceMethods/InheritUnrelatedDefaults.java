interface B { default void foo() {} }
interface C { default void foo() {} }
class <error descr="D inherits unrelated defaults for foo() from types B and C">D</error> implements B, C {}

interface E {
  default void foo() {
  }
}

interface F {
  void foo();
}

interface <error descr="G inherits abstract and default for foo() from types E and F">G</error> extends E, F {}

interface H {
  default void m() {}
}
interface K {
  default void m() {}
}
class L implements H {}
class <error descr="M inherits unrelated defaults for m() from types H and K">M</error> extends L implements H, K {}