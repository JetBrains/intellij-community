// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.util.List;
import java.util.Objects;

public class Main {
  private final Point field = new Point(0, 0);

  public Point find(List<Point> points) {
      return points.stream().filter(Objects::nonNull).findFirst().orElse(field);
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