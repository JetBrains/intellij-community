class C {
  static class MyResource implements AutoCloseable {
    @Override public void close() { }
  }

  void m1() throws Exception {
    try (MyResource r1 = new MyResource()) {
      System.out.println(r1);
    }

    MyResource r2 = new MyResource();
    try (MyResource r = r2) {
      System.out.println(r);
      System.out.println(r2);
    }
  }

  void m2() throws Exception {
    try (MyResource <warning descr="Variable 'r2' is never used">r2</warning> = new MyResource()) { }

    MyResource <warning descr="Variable 'r3' is never assigned">r3</warning>;
    try (MyResource r = <error descr="Variable 'r3' might not have been initialized">r3</error>) {
      System.out.println(r);
    }
  }

  interface MyResourceProvider {
    MyResource getResource();
  }

  void m3() throws Exception {
    MyResourceProvider provider = null;
    try (MyResource r = <warning descr="Method invocation 'provider.getResource()' may produce 'java.lang.NullPointerException'">provider.getResource()</warning>) {
      System.out.println(r);
    }
  }

  void m4() {
    try (MyResource r = null) {
      System.out.println(r);
    }
  }
}