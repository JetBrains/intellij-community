// "Collapse loop with stream 'findFirst()'" "false"

import java.util.List;

public class Main {
  public Point find(List<Point> points, Main other) {
    for (Point pt : poin<caret>ts) {
      if (pt != null) return pt;
    }
    return new Point(0, 0);
  }


  static class Point {
    private int x;
    private int y;

    Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }
}