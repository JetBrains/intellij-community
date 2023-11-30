class X {
  interface I<A> {
    void foo(A a1, A a2);
  }

  {
    I<String> c = (String o1, String o2) -> {};
  }
}