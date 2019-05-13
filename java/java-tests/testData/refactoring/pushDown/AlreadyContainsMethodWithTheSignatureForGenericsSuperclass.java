class Parent<T> {
  public void fo<caret>o(T p) {
    System.out.println("a");
  }
}

class Child extends Parent {
  public void foo(Object p) {
    System.out.println("b");
  }
}