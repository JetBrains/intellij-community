
/// @param point1 point1
/// @param point2 point2
/// @param i      i

record Re<caret>ct(Point point1, Point point2, int i) {}
record Point(int y, int x) {}

class Use {
  void foo(Object obj) {
    switch (obj) {
      case Rect(Point point1, Point point2, int i) when point2.x() == 42 -> System.out.println(point2);
      case Rect(Point(int y1, int x1), Point(int y2, int x2), int i) when y2 == x2 -> System.out.println(y2);
      case Rect(Point(int x1, int y1), Point(int x2, int y2), int i) -> System.out.println(y2);
      default -> throw new IllegalStateException("Unexpected value: " + obj);
    }
  }
}
