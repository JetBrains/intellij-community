
class FirstClass {
  public FirstClass(int i) {
  }

  public FirstClass() {
    this(Point.FOO);
  }

  public class Point {
    public static final int FOO = 0;
  }
}
