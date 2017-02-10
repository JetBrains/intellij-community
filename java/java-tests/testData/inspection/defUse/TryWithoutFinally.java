class TryWithoutFinally {
  static class Resource implements AutoCloseable {
    public void close() throws Exception { }
    boolean find() { throw new UnsupportedOperationException(); }
  }

  void test() throws Exception {
    boolean found = <warning descr="Variable 'found' initializer 'false' is redundant">false</warning>;
    try (Resource r = new Resource()) {
      found = r.find();
    }
    System.out.println(found);
  }
}