
import java.util.List;
import java.util.function.Function;


interface Foo<T> {

  <R> Foo<R> map(Function<T, R> mapper);

  Foo<T> onClose();
}

class Bar {
  Foo<List<String>> transform(final Foo<? extends String> foo) {
    return foo
      .map(v2 -> tuple(v2))
      .onClose();
  }

  static <T2> List<T2> tuple(T2 v2) {
    return null;
  }
}