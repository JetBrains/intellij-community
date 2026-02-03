// "Replace method reference with lambda" "true-preview"
import java.util.function.*;

public class MyTest {
  class Inner<I> {
    Inner() {}
  }
  <T> void test() {
    Supplier<Inner<T>> supplier2 = <caret>Inner::new;
  }
}
