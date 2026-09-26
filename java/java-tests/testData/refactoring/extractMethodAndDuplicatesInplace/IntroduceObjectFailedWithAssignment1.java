public class Test {

  void test(boolean p) {
    <selection>int x = 42;
    int y = 0;
    System.out.println();</selection>

    if (p) {
      x = (x + y)/2;
    }

    System.out.println("Point(" + x + ", " + y + ")");
  }
}