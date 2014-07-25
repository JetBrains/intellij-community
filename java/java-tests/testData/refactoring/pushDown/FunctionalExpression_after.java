interface Base {
    default void foo() {
        System.out.println("Hi there.");
    }
}

class Test {
  {
    Base base = () -> {};
  }
}
