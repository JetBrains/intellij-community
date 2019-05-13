
import java.util.function.Consumer;

class Test {
  private static <T> void _f(Consumer<T> c) {}

  private static void bar(final String s) {}

  public static void main(String[] argArr) {
    Test.<String>_f(Test::bar);
  }
}
