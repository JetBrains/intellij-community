import java.util.function.Function;

interface Test<R> {

  static <T, R> Inner<R> go(T t, Function<T, R> f) {
    return new Inner<>();
  }

  class Inner<R> implements Test<R> {

    <T> Inner<R> go(T t, Function<T, R> f) {
      return new Inner<>();
    }
  }


}
class App {

  void run(Test.Inner<Integer> go) {

    Test.Inner<Integer> test = go.go(1, (Integer i) -> i);
  }
}

