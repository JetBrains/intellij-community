// "Replace with 'peek'" "false"

import java.util.stream.IntStream;

public class Main {
  void test() {
    int[] ints = IntStream.range(0, 100)
      .filter(x -> x % 3 == 0)
      .m<caret>ap(x -> {
        x++;
        return x;
      })
      .toArray();
    System.out.println(ints.length);
  }
}
