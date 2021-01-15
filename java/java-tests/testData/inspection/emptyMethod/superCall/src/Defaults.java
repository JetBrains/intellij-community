
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

interface Interface4 {
  void foo();
}
interface Interface5 extends Interface1, Interface4 {
  @Override default void foo() { Interface1.super.foo(); }
}

interface Interface6 extends Interface1 {
  void foo();
}
interface Interface7 extends Interface1, Interface6 {
  @Override default void foo() { Interface1.super.foo(); }
}

class Main {
  public static void main(String args[]) {
    new Interface5() { }.foo();
    new Interface7() { }.foo();
  }
}

