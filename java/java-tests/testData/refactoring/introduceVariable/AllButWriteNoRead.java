public class AllButWriteNoRead {
  static class X {
    static int y;
  }

  void test() {
    <selection>X.y</selection> = 10;
    System.out.println("hello");
    X.y = 15;
  }
}