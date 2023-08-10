// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.awt.*;
import java.util.List;
import java.util.Objects;

public class Main {
  private static final Point ZERO = new Point(0, 0);

  public static Point find(List<Point> points) {
      return points.stream().filter(Objects::nonNull).findFirst().orElse(ZERO);
  }
}