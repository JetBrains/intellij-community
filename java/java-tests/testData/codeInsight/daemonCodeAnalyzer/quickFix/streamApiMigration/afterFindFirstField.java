// "Replace with findFirst()" "true"

import java.awt.*;
import java.util.List;
import java.util.Objects;

public class Main {
  private Point field = new Point(0, 0);

  public Point find(List<Point> points) {
      return points.stream().filter(Objects::nonNull).findFirst().orElse(field);
  }
}