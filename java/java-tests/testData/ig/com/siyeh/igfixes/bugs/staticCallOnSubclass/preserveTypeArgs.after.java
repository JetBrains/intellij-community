class Super {
  static <T> void foo() {}
}

class Child extends Super {}

class Bar {
  {
      Super.<String>foo();
  }
}