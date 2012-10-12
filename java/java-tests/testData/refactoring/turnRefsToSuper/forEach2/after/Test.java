class Test {
  void test() throws Exception {
    MyIterable r = new MyIterableImpl();
    for (String s : r) {
      r.length();
    }
  }

  interface MyIterable extends Iterable<String> {
  }

  static class MyIterableImpl implements MyIterable {
    public Iterator<String> iterator() { return null; }
  }
}