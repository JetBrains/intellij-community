// "Replace 'mapToInt()' with 'map()'" "true"
import java.util.stream.*;

class Test {
  void test() {
    LongStream.range(0, 100).mapToInt(x -> x<caret>*2).forEach(s -> System.out.println(s));
  }
}