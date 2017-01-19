// "Replace IntStream.range().map() with collection.stream()" "false"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Test extends ArrayList<String> {
  public void test(List<List<String>> list) {
    int[] arrWrong = IntStream.range(0, list.size())
      .m<caret>ap(index -> list.get(index).size() + list.get(0).get(index).length()).toArray();
  }
}
