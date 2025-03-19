import java.util.function.*;

public class MyTest {
  class Inner<I> {
    Inner() {}
  }
  <T> void test() {
    Supplier<Inner<T>> supplier2 = <caret>() -> new Inner<T>();
  }
}
