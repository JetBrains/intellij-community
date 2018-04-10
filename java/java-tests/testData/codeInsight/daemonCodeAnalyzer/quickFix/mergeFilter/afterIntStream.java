// "Merge filter chain" "true"

import java.util.stream.IntStream;

public class Main {
  void test() {
    System.out.println(IntStream.range(0, 100).filter(x -> x > 20 && x < 50).count());
  }
}