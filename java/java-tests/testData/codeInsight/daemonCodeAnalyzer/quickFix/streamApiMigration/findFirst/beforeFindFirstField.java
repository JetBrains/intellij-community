// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.awt.*;
import java.util.List;

public class Main {
  private final Point field = new Point(0, 0);

  public Point find(List<Point> points) {
    for (Point pt : poin<caret>ts) {
      if (pt != null) return pt;
    }
    return field;
  }
}