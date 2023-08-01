class Super {
  static <T> void foo() {}
}

class Child extends Super {}

class Bar {
  {
    Child.<String>f<caret>oo();
  }
}