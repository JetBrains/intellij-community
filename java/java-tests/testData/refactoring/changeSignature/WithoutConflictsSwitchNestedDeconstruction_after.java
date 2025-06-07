public class WithoutConflictsSwitchNestedDeconstruction {
  public static void main(String[] args) {
    final Point point = new Point(199);
    point.foo(point);
  }
  public record Point(int x) {
    void foo(Object obj) {
      switch (obj) {
        case Point(int x) -> {
          System.out.println("y2");
        }
        default -> {
          System.out.println("default");
        }
      }
    }
  }
}