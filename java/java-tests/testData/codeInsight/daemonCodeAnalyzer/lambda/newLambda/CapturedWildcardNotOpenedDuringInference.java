
import java.util.List;
import java.util.function.Function;


interface Foo<T> {

  <R> Foo<R> map(Function<T, R> mapper);

  Foo<T> onClose();
}

class Bar {
  Foo<List<String>> transform(final Foo<? extends String> foo) {
    <error descr="Incompatible types. Found: 'Foo<? extends java.util.List<? extends java.lang.String>>', required: 'Foo<java.util.List<java.lang.String>>'">return foo
      .map(v2 -> tuple(v2))
      .onClose();</error>
  }

  static <T2> List<T2> tuple(T2 v2) {
    return null;
  }
}