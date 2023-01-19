/**
 * @param point1 point1
 * @param point2 point2
 * @param i      i
 */
record Rect(Point point1, Point point2, int i) {
  Rec<caret>t(Point point1, Point point2, int i) {
    this.point1 = point1;
    this.point2 = point2;
    this.i = i;
  }
}
record Point(int y, int x) {}

class Use {
  void foo(Object obj) {
    switch (obj) {
      case Rect(Point point1, Point point2, int i) when point1.equals(point2) -> {}
      case Rect(Point(int y1, int x1) point1, Point(int y2, int x2), int i) rect when x1 == x2 -> System.out.println(point1);
      case ((Rect(((Point(((int x1)), ((int y1))))), Point(((int x2)), ((int y2))) point2, ((int i))))) -> System.out.println(point2);
      default -> {}
    }
  }
}
