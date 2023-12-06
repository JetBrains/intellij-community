class X {
  static void foo(X x) {
    System.out.println(x);
  }

  void call(X x) {
    foo<caret>(x);
  }
}