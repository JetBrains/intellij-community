import java.io.IOException;

class TryWithResources {
  final AutoCloseable f1 = null;
  AutoCloseable f2 = null;

  void testFields() throws Exception {
    try (f1; <error descr="Variable used as a try-with-resources resource should be final or effectively final">f2</error>) { }
    catch (Exception ignore) { }
  }

  void testLocalVars() throws Exception {
    final AutoCloseable r1 = null;
    AutoCloseable r2 = null;
    AutoCloseable r3 = null;
    try (r1; r2; <error descr="Variable used as a try-with-resources resource should be final or effectively final">r3</error>) { }
    r3 = null;
  }

  void testType() throws Exception {
    String s = "";
    try (<error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.AutoCloseable'">s</error>;
         <error descr="Incompatible types. Found: 'TryWithResources', required: 'java.lang.AutoCloseable'">this</error>) { }
  }

  void testUnhandled() {
    class Resource implements AutoCloseable {
      @Override public void close() throws IOException { }
    }
    Resource r = new Resource();
    try (<error descr="Unhandled exception from auto-closeable resource: java.io.IOException">r</error>) { }
  }

  void testResolve() throws Exception {
    try (<error descr="Cannot resolve symbol 'r'">r</error>; AutoCloseable r = null) { }
    try (AutoCloseable r = null; r) { }
  }

  void testUnassigned() throws Exception {
    AutoCloseable r;
    try (<error descr="Variable 'r' might not have been initialized">r</error>) {
      System.out.println(r);
    }
  }
}
