import org.jetbrains.annotations.Nullable;

class SkipSwitchExpressionWithThrow {
  static boolean test(int x) {
    return switch (x) {
      case 404 -> false;
      case 401 -> throw new RuntimeException();
      case 403 -> throw new IllegalArgumentException();
      default -> throw new IllegalStateException();
    };
  }

  public enum Foo {
    A, B
  }

  public static <R> R exception() {
    throw new RuntimeException();
  }

  @Nullable
  public static Object test2(Foo foo) {
    return switch (foo) {
      case A -> null;
      case B -> exception();
    };
  }
}