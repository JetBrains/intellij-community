
interface Interface1 {
  default void foo() { System.out.println("Interface1.foo()"); }
}

interface Interface2 {
  default void foo() { System.out.println("Interface2.foo()"); }
}

interface Interface3 extends Interface1, Interface2 {
  default void foo() {
    Interface1.super.foo();
    Interface2.super.foo();
  }
}