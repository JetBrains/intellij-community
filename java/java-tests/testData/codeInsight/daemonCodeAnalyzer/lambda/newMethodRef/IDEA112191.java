import java.util.function.ToIntFunction;
class Test {
  {
    fooBar(String::length);
  }

  class Foo<K> {
    Foo<K> then() {
      return null;
    }
  }
  static <T> Foo<T> fooBar(ToIntFunction<? super T> keyExtractor) {
    return null;
  }
}