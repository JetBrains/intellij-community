class C {
  static class E extends Exception { }
  static class E1 extends E { }
  static class E2 extends E { }
  static class E3 extends E { }

  static class MyResource implements AutoCloseable {
    public MyResource() throws E1 { }
    public void doSomething() throws E2 { }
    @Override public void close() throws E3 { }
  }

  void m1() {
    try (MyResource r = new MyResource()) { r.doSomething(); }
    catch (E1 | E2 | E3 ignore) { }

    try (new MyResource()) { }
    catch (E1 | E3 ignore) { }
  }

  void m2() throws Exception {
    try (<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.AutoCloseable'">Object r = new MyResource()</error>) { }

    try (<error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.AutoCloseable'">"resource"</error>) { }
  }

  void m3() throws Exception {
    try (MyResource r = new MyResource()) {
      r.doSomething();
    }
    catch (E e) {
      <error descr="Cannot resolve symbol 'r'">r</error> = null;
    }
    finally {
      <error descr="Cannot resolve symbol 'r'">r</error> = null;
    }
    <error descr="Cannot resolve symbol 'r'">r</error> = null;

    MyResource r = null;

    try (MyResource <error descr="Variable 'r' is already defined in the scope">r</error> = new MyResource()) { }

    try (r = new MyResource()) { }
  }
}