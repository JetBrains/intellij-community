public class WithoutConflictsSwitchExtendedDeconstruction {
  public static void main(String[] args) {
    final Point point = new Point(199, null);
    point.foo(point);
  }
  public record Point(int a, CharSequence b<caret>) {
    void foo(Object obj) {
      switch (obj) {
        case Point(int x, Object y) -> {
          System.out.println("1");
        }
        default -> {
          System.out.println("default");
        }
      }
    }
  }
}