
interface A {
  default void f(){}
}
interface B extends A {
  void f();
}
interface C extends A {}
interface D extends C {}

class U {
  <T extends B & C> void m (){}
  <T extends B & D> void m1(){}
}