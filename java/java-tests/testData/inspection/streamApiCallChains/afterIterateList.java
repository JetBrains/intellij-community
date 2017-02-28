// "Replace IntStream.range().mapToObj() with list.stream()" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Test extends ArrayList<String> {
  public void test(List<List<String>> list) {
    List<?>[] arr = list.stream().toArray(List[]::new);
  }
}
