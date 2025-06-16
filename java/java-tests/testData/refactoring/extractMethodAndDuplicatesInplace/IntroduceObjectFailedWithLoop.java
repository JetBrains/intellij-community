public class Test {

  void test() {
    double x = 0;
    double y = 0;
    while (x < 10) {
      <selection>x = x + .1;
      y = y - .1;</selection>
    }
    System.out.println("x: " + x + "; y: " + y);
  }
}