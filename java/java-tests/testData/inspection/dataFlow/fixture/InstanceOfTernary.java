class Test {
  void test(Object obj) {
    if ((obj instanceof CharSequence ? (CharSequence)obj : null) instanceof String) {

    }
  }
}