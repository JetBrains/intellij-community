class P<A, B> {
  static <C, D> P<C, D> create(C c, D d) {
    return null;
  }

  P<String, P<Integer, String>> fooBar(String s, String s1) {
    return create(s, create(1, s1));
  }
}