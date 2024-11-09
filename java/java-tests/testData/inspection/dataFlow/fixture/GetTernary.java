import java.io.IOException;
import java.io.InputStream;

public class GetTernary {
  static class Point {
    int x, y;
    
    Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }
  
  void test(boolean b, Point p1, Point p2) {
    int x = (b ? p1 : p2).x;
    if (x == 1) {
      if (b && <warning descr="Condition 'p1.x == 1' is always 'true' when reached">p1.x == 1</warning>) {}
    }
    if (p1.x == 2 && p2.x == 2) {
      int x1 = (b ? p1 : p2).x;
      if (<warning descr="Condition 'x1 == 2' is always 'true'">x1 == 2</warning>) {

      }
    }
  }
}