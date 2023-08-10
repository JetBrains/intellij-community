class X {
  void foo(X x) {
    System.out.println(x);
  }

  class Y {
    void call(X x, X y) {
      x.foo(X.this);
    }
  }
}