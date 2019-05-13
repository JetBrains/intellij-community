
interface A {
  default void f(){}
}
interface B extends A {
  void f();
}
interface C extends A {}
interface D extends C {}
interface E {
  default void f() {}
}
class U {
  <T extends B & C> void m (){}
  <T extends B & D> void m1(){}
  <<error descr="T inherits abstract and default for f() from types E and B">T</error> extends B & E> void m2(){}
}