// "Replace 'map()' with 'mapToDouble()'" "true"
import java.util.stream.*;

class Test {
  void test() {
    IntStream.range(0, 100).map(x -> x/<caret>1.0).forEach(s -> System.out.println(s));
  }
}