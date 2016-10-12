interface Test {
  default void foo() {
    System.out.println();
  }
}

interface A extends Test {}