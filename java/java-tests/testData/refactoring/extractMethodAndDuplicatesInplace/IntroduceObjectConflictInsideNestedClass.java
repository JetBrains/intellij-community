public class Test {

  class Result {}

  private static class Nested {
    void test() {
      <selection>int x = 0;
      int y = 0;
      System.out.println();</selection>

      System.out.println("Point(" + x + ", " + y + ")");
    }
  }

}