import java.util.function.*;

public class MyTest {
  <T> void test() {
    class Local<L> {
      Local() {}
    }
    Supplier<Local<T>> supplier = <caret>Local::new;
  }
}
