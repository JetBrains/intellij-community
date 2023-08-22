import java.util.*;
import org.jetbrains.annotations.Range;

public class ForEachPattern {
  record IntBox(int i) {}
  void bar1(Iterable<IntBox> i) {
    int a = 1;
    for (IntBox(int d) : i) {
      a = 2;
    }
    System.out.println(a == 1);
  }

  record Point(int x, @Range(from = 1, to = 10) int y) {}

  public static void main(String[] args) {
    List<Point> points = new ArrayList<>();
    use(points);
  }

  private static void use(List<Point> points) {
    int a = 0, b = 0;
    for (Point(int x, int y) : points) {
      if (x == 1) {
        a = 1;
      }
      if (x == 2) {
        b = 1;
      }
      if (x == 2 && <warning descr="Condition 'b == 1' is always 'true' when reached">b == 1</warning>) {
        b = y;
      }
      if (<warning descr="Condition 'y == 12' is always 'false'">y == 12</warning>) {}
    }
    if (a == 1 && b == 1) {

    }
  }
}