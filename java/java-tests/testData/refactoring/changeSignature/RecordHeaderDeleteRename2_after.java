/**
 * @param p2 point2
 */
record Rect(Point p2) {}
record Point(int y, int x) {}

class Use {
  void foo(Object obj) {
    switch (obj) {
      case Rect(Point p2) when p2.x() == 42 -> System.out.println(p2);
      case Rect(Point(int y2, int x2)) rect when x1 == x2 -> System.out.println(point1);
      case ((Rect(((Point(((int x2)), ((int y2))) p2))))) -> System.out.println(p2);
      default -> throw new IllegalStateException("Unexpected value: " + obj);
    }
  }
}
