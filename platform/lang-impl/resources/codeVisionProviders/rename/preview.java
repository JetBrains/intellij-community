// Foo.java
class Foo {
  public static void foo2() {}
}

// Bar.java
class Bar {
  public void bar() {
    Foo.foo(); // Has been renamed to foo2
  }
}