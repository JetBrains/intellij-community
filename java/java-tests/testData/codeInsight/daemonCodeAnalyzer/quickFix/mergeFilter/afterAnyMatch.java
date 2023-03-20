// "Merge filter chain" "true-preview"

import java.util.stream.LongStream;

public class Main {
  void test() {
    System.out.println(LongStream.range(0, 100).anyMatch(x -> x > 20 && x < 50));
  }
}