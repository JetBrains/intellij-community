// "Replace with findFirst()" "false"

import java.awt.*;
import java.util.List;

public class Main {
  private Point field = new Point(0, 0);

  public Point find(List<Point> points, Main other) {
    for (Point pt : poin<caret>ts) {
      if (pt != null) return pt;
    }
    return other.field;
  }
}