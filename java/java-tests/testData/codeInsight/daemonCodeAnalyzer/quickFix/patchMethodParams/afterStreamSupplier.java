// "Wrap lambda return using 'String.valueOf()'" "true-preview"
import java.util.stream.*;

class Demo {
  void test() {
    Stream<String> stream = Stream.generate(() -> String.valueOf(Math.random()));
  }
}
