// "Replace IntStream.range().map() with list.stream()" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Test extends ArrayList<String> {
  public void test(List<List<String>> list) {
    int[] arr1 = IntStream.range(0, list.size()).m<caret>ap(index -> list.get(index).size()).toArray();
  }
}
