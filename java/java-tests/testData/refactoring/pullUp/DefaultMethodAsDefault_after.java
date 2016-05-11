interface A {
    default void foo() {
      System.out.println("in B");
    }
}
interface B extends A {
}