public class WithoutConflictsSwitchExtendedDeconstruction {
  public static void main(String[] args) {
    final Point point = new Point(199);
    point.foo(point);
  }
  public record Point(int a) {
    void foo(Object obj) {
      switch (obj) {
        case Point(int x) -> {
          System.out.println("1");
        }
        default -> {
          System.out.println("default");
        }
      }
    }
  }
}