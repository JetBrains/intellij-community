public class Test {

  void test() {
    int conflict = 42;
    <selection>int x = 0;
    int y = 0;
    System.out.println();</selection>

    System.out.println("Point(" + x + ", " + y + ")");
  }
}