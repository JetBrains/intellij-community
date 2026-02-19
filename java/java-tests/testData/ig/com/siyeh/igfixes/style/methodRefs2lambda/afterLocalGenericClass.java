// "Replace method reference with lambda" "true-preview"
import java.util.function.*;

public class MyTest {
  <T> void test() {
    class Local<L> {
      Local() {}
    }
    Supplier<Local<T>> supplier = <caret>() -> new Local<T>();
  }
}
