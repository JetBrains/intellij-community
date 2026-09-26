import java.util.function.Function;

class MyTest {
 
  <T> java.util.List<T> f(Function<T, String> ff) {
    return null;
  }

  void test() {
    var t = this.<String><caret>f(t);
  }
}