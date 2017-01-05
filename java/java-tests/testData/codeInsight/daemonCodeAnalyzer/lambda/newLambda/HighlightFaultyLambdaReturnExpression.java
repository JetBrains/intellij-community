import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

class Test {
  public static void bar(boolean f) {
    foo(() -> {
      if (f) <error descr="Missing return value">return;</error>
      if (false) <error descr="Missing return value">return;</error>
      if (!f) return <error descr="Bad return type in lambda expression: int cannot be converted to String">1</error>;
      return null;
    });
  }

  public static void foo(Supplier<String> consumer) {}

  private void foo(List<String> descriptions) {
    Collections.sort(descriptions, (o1, o2) -> {
      final int elementsDiff = o1.length() - o2.length();
      if (elementsDiff == 0) {
        return <error descr="Bad return type in lambda expression: boolean cannot be converted to int">o1.equals(o2)</error>;
      }
      return -elementsDiff;
    });
  }
}