public class ConflictsSwitchNestedDeconstructionDifTypes {
  public static void main(String[] args) {
    final Point point = new Point(199, null);
    point.foo(point);
  }
  public record Point(Object x<caret>, Point y) {
    void foo(Object obj) {
      switch (obj) {
        case Point(Object x, Point(Integer x2, Point y2)) -> {
          System.out.println("y2");
        }
        default -> {
          System.out.println("default");
        }
      }
    }
  }
}