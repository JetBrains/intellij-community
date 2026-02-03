public class ConflictsSwitchUsedDeconstruction {
  public record Point(int a<caret>, String b) {
    void foo(Object obj) {
      switch (obj) {
        case Point(int x, Object y) when x > 10 -> {
        }
        default -> {
        }
      }
    }
  }
}