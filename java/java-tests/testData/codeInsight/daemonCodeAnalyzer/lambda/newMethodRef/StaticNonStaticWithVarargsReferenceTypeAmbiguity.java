class Test {

  static class A {
    void foo(A... as) {}
    static void foo(A a) {}
  }

  interface I {
    void bar(A a);
  }

  static void test() {
    I i = A::<error descr="Reference to 'foo' is ambiguous, both 'foo(A)' and 'foo(A...)' match">foo</error>;
  }
}
