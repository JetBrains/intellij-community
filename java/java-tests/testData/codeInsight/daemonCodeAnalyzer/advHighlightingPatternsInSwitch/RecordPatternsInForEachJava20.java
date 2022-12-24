class Main {
  record Point(int x, int y) {}
  record EmptyBox() {}

  void test1(Point[] points) {
    for (Point(int x, int y) : points) {
      System.out.println(x + y);
    }
  }

  void test2(EmptyBox[] emptyBoxes) {
    for (EmptyBox() : emptyBoxes) {
      System.out.println("Fill it up and send it back");
    }
  }
}
