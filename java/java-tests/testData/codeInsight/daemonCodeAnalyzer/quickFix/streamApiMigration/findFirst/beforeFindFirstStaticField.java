// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.awt.*;
import java.util.List;

public class Main {
  private static final Point ZERO = new Point(0, 0);

  public static Point find(List<Point> points) {
    for (Point pt : point<caret>s) {
      if (pt != null) return pt;
    }
    return ZERO;
  }
}