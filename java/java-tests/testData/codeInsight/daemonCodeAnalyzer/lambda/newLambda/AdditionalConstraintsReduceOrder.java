import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;

class FooBar<K> {
  void foo(List<K > s) {}
  <T, U> List<T> bar(BinaryOperator<U> kk, Function<T, U> f){
    return null;
  }

  void f(FooBar<Integer> integerFooBar){
    integerFooBar.foo(bar((a, b) -> a + b, x -> 1));
  }
}
