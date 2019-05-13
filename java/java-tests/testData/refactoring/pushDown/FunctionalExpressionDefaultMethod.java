interface Base {
    default void foo<caret>() {
        System.out.println("Hi there.");
    }
    void bar();
}

class Test {
  {
    Base base = () -> {};
  }
}

class Child implements Base {}
