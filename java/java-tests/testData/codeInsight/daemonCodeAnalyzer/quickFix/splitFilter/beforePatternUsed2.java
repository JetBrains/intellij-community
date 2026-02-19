// "Split into filter chain" "false"
import java.util.List;

class X {
  void test(List<?> points) {
    points.stream().filter(p -> p instanceof Point(double x, double y) &<caret>& x > y).filter(p -> p.hashCode() > 0).forEach(System.out::println);
  }
}

record Point(double x, double y) {
}