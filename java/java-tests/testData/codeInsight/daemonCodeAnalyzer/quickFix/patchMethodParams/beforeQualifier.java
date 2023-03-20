// "Convert argument to 'long'" "true-preview"
import java.util.stream.Stream;

public class Demo {
  void test() {
    Stream<Long> stream = Stream.of(123)<caret>.limit(10);
  }
}