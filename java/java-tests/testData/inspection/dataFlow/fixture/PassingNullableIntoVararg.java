import org.jetbrains.annotations.NotNull;

class Test {
  public static void test(@NotNull Object... objects) { }

  public static void main(String[] args) {
    Object o = null;
    test(o);
  }
}