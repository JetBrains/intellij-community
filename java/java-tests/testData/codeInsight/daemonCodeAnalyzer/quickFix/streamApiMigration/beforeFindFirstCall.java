// "Replace with findFirst()" "false"

import java.awt.*;
import java.util.List;

public class Main {
  public Point find(List<Point> points, Main other) {
    for (Point pt : poin<caret>ts) {
      if (pt != null) return pt;
    }
    return new Point(0, 0);
  }
}