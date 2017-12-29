// "Merge filter chain" "true"

import java.util.stream.LongStream;

public class Main {
  void test() {
    System.out.println(LongStream.range(0, 100).anyMatch(x -> x > 20 && x < 50));
  }
}