class Foo {
  static class Bar {
    static final String s = <error descr="Non-static method 'toString()' cannot be referenced from a static context">toString</error>(1);
  }
  static String toString(int x) {
    return x + "";
  }
}