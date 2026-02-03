import java.util.function.Function;

class Test {
  static void foo(Function<? super String, ? extends Number> f) {}

  static void test() {
    foo((Object o) -> o.hashCode());
  }
}