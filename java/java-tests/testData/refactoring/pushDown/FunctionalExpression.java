interface Base {
    default void foo() {
        System.out.println("Hi there.");
    }
    void ba<caret>r();
}

class Test {
  {
    Base base = () -> {};
  }
}
