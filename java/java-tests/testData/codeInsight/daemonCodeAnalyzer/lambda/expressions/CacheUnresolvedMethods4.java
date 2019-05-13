import java.util.function.Function;
import java.util.stream.Stream;

class Test {
  <K> void foo(Function<String, K> f1) {}

  <T> Stream<T> bar(T ts) {
    return null;
  }

  void f(){
    foo(y -> bar(y.to<caret>String()).map(a -> a.length()));
  }
}