import org.jetbrains.annotations.NotNull;

public class InterfaceStaticMethodParameter {
  public static void test() {
    I.test(null);
  }
}

interface I {
  static void test(@NotNull String s) { }
}