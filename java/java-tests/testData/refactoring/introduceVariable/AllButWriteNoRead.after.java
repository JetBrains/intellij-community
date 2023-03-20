public class AllButWriteNoRead {
  static class X {
    static int y;
  }

  void test() {
      int x = X.y;
      x = 10;
    System.out.println("hello");
    x = 15;
  }
}