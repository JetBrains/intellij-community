class Test {
  static class MyResource implements AutoCloseable {
    @Override public void close() { }
  }

  interface MyResourceProvider {
    MyResource getResource();
  }

  void m1() throws Exception {
    MyResourceProvider provider = null;
    try (MyResource r = <warning descr="Method invocation 'provider.getResource()' may produce 'java.lang.NullPointerException'">provider.getResource()</warning>) {
      System.out.println(r);
    }
  }

  void m2() {
    try (MyResource r = null) {
      System.out.println(r);
    }
  }

  static class ResourcefulException1 extends Exception { }
  static class ResourcefulException2 extends Exception { }

  static class ExceptionalResource implements AutoCloseable {
    @Override public void close() throws ResourcefulException1 { }
  }

  ExceptionalResource provideExceptionalResource() throws ResourcefulException2 {
    return new ExceptionalResource();
  }

  void m3() {
    try (ExceptionalResource r = provideExceptionalResource()) {
      System.out.println(r);
    }
    catch (Exception e) {
      if (e instanceof ResourcefulException1) {
        System.out.println("1");
      }
      else if (e instanceof ResourcefulException2) {
        System.out.println("2");
      }
    }
  }
}