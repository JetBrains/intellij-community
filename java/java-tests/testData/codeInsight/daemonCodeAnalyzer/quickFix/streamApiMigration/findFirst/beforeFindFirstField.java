// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.util.List;

public class Main {
  private final Point field = new Point(0, 0);

  public Point find(List<Point> points) {
    for (Point pt : poin<caret>ts) {
      if (pt != null) return pt;
    }
    return field;
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