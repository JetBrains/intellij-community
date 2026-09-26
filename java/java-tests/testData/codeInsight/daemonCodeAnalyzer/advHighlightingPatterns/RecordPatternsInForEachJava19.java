import java.util.List;

class Main {
  record Point(int x, int y) {}
  record EmptyBox() {}

  void test1(Point[] points) {
    for (<error descr="Record patterns in for-each loops are not supported at language level '19'">Point(int x, int y)</error> : points) {
      System.out.println(x + y);
    }
  }

  void test2(EmptyBox[] emptyBoxes) {
    for (<error descr="Record patterns in for-each loops are not supported at language level '19'">EmptyBox()</error> : emptyBoxes) {
      System.out.println("Fill it up and send it back");
    }
  }

  void test3(List<Point> points) {
    for (<error descr="Record patterns in for-each loops are not supported at language level '19'">Point(int x)</error> : points) {
      System.out.println(x);
    }
  }
}
