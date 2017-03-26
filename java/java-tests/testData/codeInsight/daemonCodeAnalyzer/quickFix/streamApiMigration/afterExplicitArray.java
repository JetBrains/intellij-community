// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"

import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Test {
  public String test(String other) {
      return Stream.of("aaa", "bbb", "ccc", "ddd").filter(other::startsWith).findFirst().orElse(null);
  }

  public CharSequence test2(String other) {
      return Stream.<CharSequence>of("aaa", "bbb", "ccc", "ddd").filter(s -> other.startsWith(s.toString())).findFirst().orElse(null);
  }

  public int test(int other) {
      return IntStream.of(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024).filter(i -> i > other).findFirst().orElse(-1);
  }
}
