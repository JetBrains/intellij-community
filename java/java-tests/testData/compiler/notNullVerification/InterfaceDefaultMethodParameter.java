import org.jetbrains.annotations.NotNull;

public class InterfaceDefaultMethodParameter {
  public static void test() {
    new I(){}.test(null);
  }
}

interface I {
  default void test(@NotNull String s) { }
}