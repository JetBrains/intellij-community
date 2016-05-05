interface Test {
  void foo();
}

interface A extends Test {
    @Override
    default void foo() {
      System.out.println();
    }
}