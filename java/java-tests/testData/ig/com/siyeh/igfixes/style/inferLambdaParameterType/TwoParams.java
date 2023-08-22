class X {
  interface I<A> {
    void foo(A a1, A a2);
  }

  {
    I<String> c = (<caret>o1, o2) -> {};
  }
}