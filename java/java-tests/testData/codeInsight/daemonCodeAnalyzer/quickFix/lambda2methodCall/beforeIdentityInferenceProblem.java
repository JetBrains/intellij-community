// "Replace lambda expression with 'Function.identity()'" "false"
import java.util.function.Function;

public class Main {
  void m(Function<String, String> f) {
    n(f, o <caret>-> o);
  }

  <T> void n(Function<T, T> f1, Function<T, Object> f2) {}
}
