// "Collapse loop with stream 'forEach()'" "true-preview"

import java.util.stream.IntStream;

public class Test {
  void foo(int i) {}

  void test() {
      IntStream.range(0, 10).forEach(i -> System.out.println(foo(i)));
  }
}
