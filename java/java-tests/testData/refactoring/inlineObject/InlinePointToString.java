class Main {
  void test() {
    System.out.println(new <caret>Point(1, 2).toString());
  }
}

class Point {
  private int x, y;

  public Point(int _x, int _y) {
    x = _x;
    y = _y;
  }

  public String toString() {
    return "["+x+", "+y+"]";
  }
}
