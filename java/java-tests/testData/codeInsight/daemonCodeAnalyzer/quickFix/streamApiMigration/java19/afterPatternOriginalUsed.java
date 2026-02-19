// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class X {
  void test(List<Object> list) {
    List<Object> result = list.stream().filter(o -> o instanceof Rect(
            Point point1, Point(double x2, double y2) point2
    ) rect && x2 > point1.x()).collect(Collectors.toList());
  }
}

record Point(double x, double y) {
}

record Rect(Point point1, Point point2) {
}