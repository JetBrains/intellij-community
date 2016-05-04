interface A {
}

interface B extends A {
    default void foo() {
      System.out.println("");
    }
}