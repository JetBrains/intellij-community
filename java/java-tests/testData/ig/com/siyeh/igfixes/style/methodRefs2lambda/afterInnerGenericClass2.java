// "Replace method reference with lambda" "true-preview"
import java.util.function.*;

public class MyTest<T> {
  class Inner<I> {
    Inner() {}
  }
  void test() {
    Supplier<Inner<T>> supplier2 = <caret>() -> new Inner<T>();
  }
}
