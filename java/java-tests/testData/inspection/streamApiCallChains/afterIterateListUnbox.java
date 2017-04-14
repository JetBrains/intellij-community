// "Replace IntStream.range().mapToLong() with list.stream()" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Test extends ArrayList<String> {
  public void test(List<Integer> list) {
    long[] arr = list.stream().mapToLong(integer -> integer).toArray();
  }
}
