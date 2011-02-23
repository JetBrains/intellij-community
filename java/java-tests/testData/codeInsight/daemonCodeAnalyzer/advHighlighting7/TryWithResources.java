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

    MyResource r;

    try (<error descr="Unhandled exception from auto-closeable resource: C.E3">r = new MyResource()</error>) { }
    catch (E1 e) { }

    try (r = <error descr="Unhandled exception: C.E1">new MyResource()</error>) { }
    catch (E3 e) { }

    try (r = <error descr="Unhandled exception: C.E1">new MyResource()</error>) { }
  }

  void m2() throws Exception {
    try (<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.AutoCloseable'">Object r = new MyResource()</error>) { }

    try (<error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.AutoCloseable'">"resource"</error>) { }
  }

  void m3(int p) throws Exception {
    try (MyResource r = new MyResource()) {
      r.doSomething();
      /* todo: < error descr="Cannot assign a value to final variable 'r'">r = null</error >;*/
    }
    catch (E e) {
      <error descr="Cannot resolve symbol 'r'">r</error> = null;
    }
    finally {
      <error descr="Cannot resolve symbol 'r'">r</error> = null;
    }
    <error descr="Cannot resolve symbol 'r'">r</error> = null;

    try (MyResource <error descr="Variable 'r' is already defined in the scope">r</error> = new MyResource(); MyResource <error descr="Variable 'r' is already defined in the scope">r</error> = new MyResource()) { }

    MyResource r = null;
    try (MyResource <error descr="Variable 'r' is already defined in the scope">r</error> = new MyResource()) { }
    try (r = new MyResource()) { }

    try (MyResource <error descr="Variable 'p' is already defined in the scope">p</error> = new MyResource()) { }
    new Runnable() {
      public void run() {
        try (MyResource p = new MyResource()) { }
        catch (E e) { }
      }
    }.run();
  }
}