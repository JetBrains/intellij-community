// "Merge filter chain" "true-preview"

import java.util.stream.LongStream;

public class Main {
  void test() {
    System.out.println(LongStream.range(0, 100).filter(x -> x > 20).anyM<caret>atch(x -> x < 50));
  }
}