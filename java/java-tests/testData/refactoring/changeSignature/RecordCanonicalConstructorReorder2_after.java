/**
 * @param i      i
 * @param point2 point2
 * @param point1 point1
 */
record Rect(int i, Point point2, Point point1) {
  Rect(int i, Point point2, Point point1) {
    this.point1 = point1;
    this.point2 = point2;
    this.i = i;
  }
}
record Point(int y, int x) {}

class Use {
  void foo(Object obj) {
    switch (obj) {
      case Rect(int i, Point point2, Point point1) when point1.equals(point2) -> {}
      case Rect(int i, Point(int y2, int x2), Point(int y1, int x1) point1) rect when x1 == x2 -> System.out.println(point1);
      case ((Rect(((int i)), Point(((int x2)), ((int y2))) point2, ((Point(((int x1)), ((int y1)))))))) -> System.out.println(point2);
      default -> {}
    }
  }
}
