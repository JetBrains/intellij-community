public class Test {
  void m() throws Exception {
    try (AutoCloseable r1 = null; AutoCloseable r2 = r1) {
      System.out.println(r1 + ", " + r2);
    }
  }
}