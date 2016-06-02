interface A {
    void foo();
}
interface B extends A {
  @Override
  default void foo() {
    System.out.println("in B");
  }
}