package b;

import a.A;

import java.util.function.Consumer;

class B extends A {
  public Consumer<Integer> callFoo() {
    return super::foo;
  }
}