// "Replace with findFirst()" "true"

import java.awt.*;
import java.util.List;

public class Main {
  private static Point ZERO = new Point(0, 0);

  public static Point find(List<Point> points) {
    for (Point pt : point<caret>s) {
      if (pt != null) return pt;
    }
    return ZERO;
  }
}