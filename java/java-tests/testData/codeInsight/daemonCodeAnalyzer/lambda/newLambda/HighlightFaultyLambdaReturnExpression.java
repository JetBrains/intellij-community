
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
}