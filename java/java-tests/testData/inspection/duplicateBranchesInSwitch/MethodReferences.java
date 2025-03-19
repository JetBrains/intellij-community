package com.example;

class Foo {

  public static void main(String[] args) {
    new Foo(Mode.A).run();
    new Foo(Mode.B).run();
  }

  public interface Supplier<T> {
      T get();
  }
  private final Supplier<MyInterface> supplier;

  public Foo(Mode mode) {
    supplier = switch (mode) {
      case A -> A::new;
      case B -> B::new; // incorrect warning happening here
    };
  }

  public void run() {
    supplier.get().doSomething();
  }

  public enum Mode {
    A,
    B
  }

  private interface MyInterface {
    void doSomething();
  }

  private static class A implements MyInterface {

    @Override
    public void doSomething() {
      System.out.println("A");
    }
  }

  private static class B implements MyInterface {

    @Override
    public void doSomething() {
      System.out.println("B");
    }
  }
}