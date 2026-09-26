import java.util.List;
import java.util.Set;

class Main {
  record EmptyBox() {}
  record Point(int x, int y) {}
  record Rect(Point point1, Point point2) {}
  record Pair<T, U>(T t, U u) {}
  record Rec(Object obj) {}

  Point[] getPoints(int x) {
    return new Point[0];
  }

  void ok1(Point[] points) {
    for (<error descr="Record patterns in for-each loops are not supported at language level '21'">Point(int x, int y)</error> : points) {
      System.out.println(x + y);
    }
  }
}

