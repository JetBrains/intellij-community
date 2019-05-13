import java.util.function.Function;

class Test {

  <U, V> void foo(Function<U, ? extends V> m) {}

  {
    foo((String e) -> e.length());
  }
}
