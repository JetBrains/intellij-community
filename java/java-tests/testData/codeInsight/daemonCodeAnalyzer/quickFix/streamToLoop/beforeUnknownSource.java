// "Replace Stream API chain with loop" "true"

import java.util.Random;
import java.util.stream.IntStream;

public class Test {
  static void test() {
    IntStream.range(1, 100)
      .filter(n -> n > 20)
      .boxed()
      .flatMapToDouble(n -> new Random(n).doubles(n))
      .filter(n -> n < 0.01)
      .fo<caret>rEach(System.out::println);
  }

  public static void main(String[] args) {
    test();
  }
}