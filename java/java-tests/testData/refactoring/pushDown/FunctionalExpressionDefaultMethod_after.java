interface Base {
    void bar();
}

class Test {
  {
    Base base = () -> {};
  }
}

class Child implements Base {
    public void foo() {
    System.out.println("Hi there.");
}
}
