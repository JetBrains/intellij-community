interface A {
  default void f<caret>oo() {
    System.out.println("");
  }
}

interface B extends A {}