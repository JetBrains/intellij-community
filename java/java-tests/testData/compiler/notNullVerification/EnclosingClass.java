import org.jetbrains.annotations.NotNull;

public class EnclosingClass {
  public static Object main() {
    return new Object() {
      void foo(@NotNull String s) {}
    };
  }
}