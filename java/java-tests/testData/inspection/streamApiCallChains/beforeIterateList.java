// "Replace 'IntStream.range().mapToObj()' with 'list.stream()'" "true-preview"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Test extends ArrayList<String> {
  public void test(List<List<String>> list) {
    List<?>[] arr = IntStream.range(0, list.size()).map<caret>ToObj(index -> list.get(index)).toArray(List[]::new);
  }
}
