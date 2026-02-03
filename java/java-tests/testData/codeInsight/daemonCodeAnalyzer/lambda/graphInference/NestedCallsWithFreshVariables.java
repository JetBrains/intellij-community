
import java.util.function.Supplier;
import java.util.stream.Stream;

class ExceptionStream {

  void foo(Supplier<Stream<String>> mapper) {
    bar(flatMap  (transform(mapper)));
  }

  <R> Stream<R> flatMap(Supplier<? extends Stream<R>> mapper) {
    return null;
  }

  void bar(Stream<String> s) {}

  <T> Supplier<? extends T> transform(Supplier<T> function) {
    return function;
  }
}
