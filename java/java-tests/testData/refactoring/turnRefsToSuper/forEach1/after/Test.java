class Test {
  void test() throws Exception {
    MyIterableImpl r = new MyIterableImpl();
    for (String s : r) {
      r.length();
    }
  }

  interface MyIterable {
  }

  static class MyIterableImpl implements MyIterable, Iterable<String> {
    public Iterator<String> iterator() { return null; }
  }
}