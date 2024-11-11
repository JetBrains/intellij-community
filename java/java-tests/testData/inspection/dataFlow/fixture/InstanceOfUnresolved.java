class Test {
  void test(<error descr="Cannot resolve symbol 'XXX'">XXX</error> foo) {
    if (<error descr="Inconvertible types; cannot cast 'XXX' to 'java.lang.CharSequence'">foo instanceof CharSequence</error>) {}
  }
  
  void test2(<error descr="Cannot resolve symbol 'XXX'">XXX</error> bar) {
    System.out.println(bar.hashCode());
    if (<error descr="Inconvertible types; cannot cast 'XXX' to 'java.lang.CharSequence'">bar instanceof CharSequence</error>) {}
  }

  void testCast(<error descr="Cannot resolve symbol 'XXX'">XXX</error> foo) {
    System.out.println(<error descr="Inconvertible types; cannot cast 'XXX' to 'java.lang.CharSequence'">(CharSequence)foo</error>);
  }
}