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
