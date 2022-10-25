/**
 * @param point2 point2
 * @param point1 point1
 * @param i      i
 */
record Rect(Point point2, Point point1, int i) {
  Rect(Point point2, Point point1, int i) {
    this.point2 = point2;
    this.point1 = point1;
    this.i = i;
  }
}
record Point(int y, int x) {}

class Use {
  void foo(Object obj) {
    switch (obj) {
      case Rect(Point point2, Point point1, int i) when point2.equals(point1) -> {}
      case Rect(Point(int y1, int x1) point2, Point(int y2, int x2), int i) rect when x1 == x2 -> System.out.println(point2);
      case ((Rect(((Point(((int x1)), ((int y1))))), Point(((int x2)), ((int y2))) point1, ((int i))))) -> System.out.println(point1);
      default -> throw new IllegalStateException("Unexpected value: " + obj);
    }
  }
}
