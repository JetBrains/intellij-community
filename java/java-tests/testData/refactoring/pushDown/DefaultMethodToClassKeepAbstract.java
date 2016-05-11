interface Test {
  default void foo() {
    System.out.println();
  }
}

class B implements Test {}