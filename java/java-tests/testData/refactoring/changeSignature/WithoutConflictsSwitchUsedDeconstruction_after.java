public class WithoutConflictsSwitchUsedDeconstruction {
  public record Point(int a) {
    void foo(Object obj) {
      switch (obj) {
        case Point(int x) when x > 10 -> {
        }
        default -> {
        }
      }
    }
  }
}