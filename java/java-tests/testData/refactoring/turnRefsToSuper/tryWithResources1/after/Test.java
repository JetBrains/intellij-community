class Test {
  void test() throws Exception {
    try (MyResourceImpl r = new MyResourceImpl()) {
      r.getName();
    }
  }

  interface MyResource {
    String getName();
  }

  static class MyResourceImpl implements MyResource, AutoCloseable {
    public String getName() { return ""; }
    public void close() throws Exception { }
  }
}