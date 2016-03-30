import java.util.List;
import java.util.function.Supplier;

class Test<K> {

  {
    Supplier<List<? super K>> s = Test::foo;
  }

  static <E> List<? super E> foo() {
    return null;
  }
}

