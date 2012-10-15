class Test {
  void test() throws Exception {
    try (MyResource r = new MyResourceImpl()) {
      r.getName();
    }
  }

  interface MyResource extends AutoCloseable {
    String getName();
  }

  static class MyResourceImpl implements MyResource {
    public String getName() { return ""; }
    public void close() throws Exception { }
  }
}