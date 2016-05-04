interface A {}
interface B extends A {
  default void f<caret>oo() {
    System.out.println("in B");
  }
}