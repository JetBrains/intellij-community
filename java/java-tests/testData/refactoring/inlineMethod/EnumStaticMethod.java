
enum A {
  A(foo("a")),
  B(foo("b")),
  C(foo("c")),
  ;

  A(final String c) {
  }

  private static String f<caret>oo(String x) {
    return x;
  }
}