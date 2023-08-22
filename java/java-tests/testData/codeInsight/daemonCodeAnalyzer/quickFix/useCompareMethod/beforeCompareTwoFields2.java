// "Fix all ''compare()' method can be used to compare numbers' problems in file" "true"
import java.util.ArrayList;
import java.util.List;
public class Sort {
  static class Point {
    int x, y;
    public Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
    @Override
    public String toString() {
      return "(" + x + ", " + y + ")";
    }
  }
  public static void main(String[] args) {
    List<Point> l = Arrays.asList(new Point(1, 0), new Point(0, 1), new Point(0, 0)));
    l.sort((o1, o2) -> o1.x < o2.x ? -1 : o1.x > o2.x ? 1 : <caret>o1.y < o2.y ? -1 : o1.y > o2.y ? 1 : 0);
    System.out.println(l);
  }
}