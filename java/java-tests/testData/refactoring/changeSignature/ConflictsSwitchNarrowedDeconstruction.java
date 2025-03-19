public class ConflictsSwitchNarrowedDeconstruction {
  public static void main(String[] args) {
    final Point point = new Point(199, null);
    point.foo(point);
  }
  public record Point(int a<caret>, CharSequence b) {
    void foo(Object obj) {
      switch (obj) {
        case Point(int x, String y) -> {
          System.out.println("1");
        }
        default -> {
          System.out.println("default");
        }
      }
    }
  }
}