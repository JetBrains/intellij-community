// Foo.java
class Foo {
  public void foo(int x, int y) {}
}

// Bar.java
class Bar extends Foo {
  // Change signature will fix this signature
  @Override
  public void foo(int x) {}
}