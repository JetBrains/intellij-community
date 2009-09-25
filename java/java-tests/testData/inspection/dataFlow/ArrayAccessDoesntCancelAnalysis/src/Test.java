import org.jetbrains.annotations.*;

public class Test {
  private static void foo(@NotNull String smth) {
  }

  public static void main(String[] args) {
    String s = args[0];
    foo(null);
  }
}