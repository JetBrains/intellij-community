public class Test {
  void m() throws Exception {
    try (AutoCloseable <caret>r = null; AutoCloseable r2 = r) {
      System.out.println(r + ", " + r2);
    }
  }
}