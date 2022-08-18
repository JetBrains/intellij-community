// "Replace 'map()' with 'mapToObj()'" "true-preview"
import java.util.stream.*;

class Test {
  void test() {
    IntStream.range(0, 100).mapToObj(String::valueOf).forEach(s -> System.out.println(s));
  }
}